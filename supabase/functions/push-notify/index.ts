// Sends an FCM push for a new group / private chat message.
// Called by the trg_message_push trigger (pg_net) with a shared secret.
// Needs the FCM_SERVICE_ACCOUNT secret: the Firebase service-account JSON
// (Firebase console → Project settings → Service accounts → Generate new private key).
// Deployed with verify_jwt = false (the trigger authenticates with x-push-secret).
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

type ServiceAccount = { project_id: string; client_email: string; private_key: string };
type Recipient = {
  token: string;
  room_id: string;
  room_kind: string;
  room_name: string;
  sender_nickname: string;
  preview: string;
  unread: number;
};

let cachedToken: { value: string; expiresAt: number } | null = null;

async function googleAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 60) return cachedToken.value;
  const key = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`OAuth token error: ${JSON.stringify(json)}`);
  cachedToken = { value: json.access_token, expiresAt: now + json.expires_in };
  return json.access_token;
}

Deno.serve(async (req: Request) => {
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  const secret = req.headers.get("x-push-secret") ?? "";
  const { data: ok } = await admin.rpc("verify_push_secret", { p_secret: secret });
  if (ok !== true) return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });

  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!saJson) return new Response(JSON.stringify({ skipped: "FCM_SERVICE_ACCOUNT not set" }), { status: 200 });
  const sa = JSON.parse(saJson) as ServiceAccount;

  const { message_id } = await req.json();
  const { data, error } = await admin.rpc("push_recipients", { p_message: message_id });
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  const recipients = (data ?? []) as Recipient[];
  if (recipients.length === 0) return new Response(JSON.stringify({ sent: 0 }), { status: 200 });

  const accessToken = await googleAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const stale: string[] = [];
  let sent = 0;

  await Promise.all(recipients.map(async (r) => {
    const isGroup = r.room_kind === "group";
    const title = isGroup ? `${r.sender_nickname} · ${r.room_name}` : r.sender_nickname;
    const body = r.unread > 1 ? `${r.preview}  (${r.unread} new)` : r.preview;
    const res = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: r.token,
          // Data-only so the app decides whether to show it (skips the chat that's open on screen).
          data: {
            room_id: r.room_id,
            room_title: isGroup ? r.room_name : r.sender_nickname,
            title,
            body,
          },
          android: { priority: "HIGH", collapse_key: r.room_id },
        },
      }),
    });
    if (res.ok) {
      sent++;
    } else {
      const err = await res.text();
      if (err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) stale.push(r.token);
    }
  }));

  if (stale.length > 0) await admin.from("device_tokens").delete().in("token", stale);
  return new Response(JSON.stringify({ sent, removed_tokens: stale.length }), {
    headers: { "Content-Type": "application/json" },
  });
});
