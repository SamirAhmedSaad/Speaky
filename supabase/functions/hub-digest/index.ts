// Sends gentle Speaky Hub summaries ("5 new messages — Ahmed: …"): at most twice a day per user,
// only after 3+ missed messages, never while they're active (rules live in hub_digest_candidates()).
// Called every 15 minutes by pg_cron (job 'hub-digest') with the push secret. Deployed with verify_jwt = false.
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

type ServiceAccount = { project_id: string; client_email: string; private_key: string };
type Candidate = { user_id: string; token: string; new_count: number; last_sender: string; last_preview: string };

const HUB_ROOM_ID = "00000000-0000-0000-0000-000000000001";

async function googleAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
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
  return json.access_token;
}

Deno.serve(async (req: Request) => {
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  const { data: ok } = await admin.rpc("verify_push_secret", { p_secret: req.headers.get("x-push-secret") ?? "" });
  if (ok !== true) return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });

  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!saJson) return new Response(JSON.stringify({ skipped: "FCM_SERVICE_ACCOUNT not set" }));
  const sa = JSON.parse(saJson) as ServiceAccount;

  const { data, error } = await admin.rpc("hub_digest_candidates");
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  const candidates = (data ?? []) as Candidate[];
  if (candidates.length === 0) return new Response(JSON.stringify({ sent: 0 }));

  const accessToken = await googleAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const stale: string[] = [];
  const notified = new Set<string>();
  let sent = 0;

  await Promise.all(candidates.map(async (c) => {
    const count = Number(c.new_count);
    const body = `${count} new messages · ${c.last_sender}: ${c.last_preview}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: c.token,
          // Data-only: the app applies quiet hours and skips it if the Hub is open.
          data: { kind: "hub_digest", room_id: HUB_ROOM_ID, room_title: "Speaky Hub", title: "Speaky Hub 🌍", body },
          // Low urgency on purpose: a summary can wait for the phone to wake normally.
          android: { priority: "NORMAL", collapse_key: "hub_digest", ttl: "21600s" },
        },
      }),
    });
    if (res.ok) {
      sent++;
      notified.add(c.user_id);
    } else {
      const err = await res.text();
      if (err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) stale.push(c.token);
    }
  }));

  if (notified.size > 0) await admin.rpc("mark_hub_notified", { p_users: [...notified] });
  if (stale.length > 0) await admin.from("device_tokens").delete().in("token", stale);
  return new Response(JSON.stringify({ candidates: candidates.length, sent, users: notified.size, removed_tokens: stale.length }), {
    headers: { "Content-Type": "application/json" },
  });
});
