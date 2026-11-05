// Sends an FCM push to every admin and moderator (except the reporter) when someone reports a user.
// Called by the trg_report_push trigger (pg_net) with the shared push secret, like push-notify.
// Uses the same FCM_SERVICE_ACCOUNT secret. Deployed with verify_jwt = false.
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

type ServiceAccount = { project_id: string; client_email: string; private_key: string };
type Recipient = { token: string; user_id: string; reporter_nickname: string; reported_nickname: string; reason: string };

const REASONS: Record<string, string> = {
  spam: "Spam",
  harassment: "Harassment or bullying",
  inappropriate: "Inappropriate content",
  scam: "Scam or fraud",
  other: "Something else",
};

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
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const secret = req.headers.get("x-push-secret") ?? "";
  const { data: ok } = await admin.rpc("verify_push_secret", { p_secret: secret });
  if (ok !== true) return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });

  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!saJson) return new Response(JSON.stringify({ skipped: "FCM_SERVICE_ACCOUNT not set" }), { status: 200 });
  const sa = JSON.parse(saJson) as ServiceAccount;

  const { report_id } = await req.json();
  const { data, error } = await admin.rpc("report_push_recipients", { p_report: report_id });
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  const recipients = (data ?? []) as Recipient[];
  if (recipients.length === 0) return new Response(JSON.stringify({ sent: 0 }), { status: 200 });

  const accessToken = await googleAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const stale: string[] = [];
  let sent = 0;
  await Promise.all(recipients.map(async (r) => {
    const res = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: r.token,
          // Data-only, like chat pushes: the app shows it and opens Reports on tap.
          data: {
            kind: "report",
            title: "🚩 New report",
            body: `${r.reporter_nickname} reported ${r.reported_nickname} · ${REASONS[r.reason] ?? r.reason}`,
          },
          android: { priority: "HIGH", collapse_key: "reports" },
        },
      }),
    });
    if (res.ok) sent++;
    else {
      const err = await res.text();
      if (err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) stale.push(r.token);
    }
  }));
  if (stale.length > 0) await admin.from("device_tokens").delete().in("token", stale);
  return new Response(JSON.stringify({ sent, removed_tokens: stale.length }), {
    headers: { "Content-Type": "application/json" },
  });
});
