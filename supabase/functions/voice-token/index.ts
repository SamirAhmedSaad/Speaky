// Signs the token a Hub voice call member needs to connect to the call's audio provider.
// The caller must already hold a seat (voice_call_start / voice_call_join) in a live call; the
// call's provider decides the token: Agora (AGORA_APP_ID, AGORA_APP_CERTIFICATE) or LiveKit
// (LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET). Keys are Supabase secrets; they never reach
// the app. A provider whose keys are missing answers 503 so the app can fall back to the other one.
// Called by signed-in users with their JWT (verify_jwt = true).
import { createClient } from "npm:@supabase/supabase-js@2";
import { RtcRole, RtcTokenBuilder } from "npm:agora-token@2.0.5";
import { AccessToken } from "npm:livekit-server-sdk@2.13.3";

const TOKEN_SECONDS = 3 * 3600;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  // The gateway already verified this JWT (verify_jwt = true): read the user from it.
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  let claims: { sub?: string; role?: string; is_anonymous?: boolean } = {};
  try {
    claims = JSON.parse(atob(jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return json({ error: "auth" }, 401);
  }
  if (!claims.sub || claims.role !== "authenticated" || claims.is_anonymous) return json({ error: "auth" }, 401);
  const userId = claims.sub;

  let body: { call_id?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "body" }, 400);
  }
  const callId = body.call_id ?? "";
  if (!callId) return json({ error: "body" }, 400);

  const [{ data: call }, { data: seat }, { data: profile }] = await Promise.all([
    admin.from("voice_calls").select("id,provider,ended_at").eq("id", callId).maybeSingle(),
    admin.from("voice_call_members").select("user_id").eq("call_id", callId).eq("user_id", userId).maybeSingle(),
    admin.from("community_users").select("nickname").eq("id", userId).maybeSingle(),
  ]);
  if (!call || call.ended_at) return json({ error: "ended" }, 410);
  if (!seat) return json({ error: "no_seat" }, 403);

  const expires = Math.floor(Date.now() / 1000) + TOKEN_SECONDS;
  if (call.provider === "agora") {
    const appId = Deno.env.get("AGORA_APP_ID");
    const cert = Deno.env.get("AGORA_APP_CERTIFICATE");
    if (!appId || !cert) return json({ error: "provider_unavailable" }, 503);
    // The channel is the call id; the user account is the community user id.
    const token = RtcTokenBuilder.buildTokenWithUserAccount(
      appId, cert, callId, userId, RtcRole.PUBLISHER, TOKEN_SECONDS, TOKEN_SECONDS,
    );
    return json({ provider: "agora", app_id: appId, channel: callId, token, expires });
  }

  const url = Deno.env.get("LIVEKIT_URL");
  const key = Deno.env.get("LIVEKIT_API_KEY");
  const secret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!url || !key || !secret) return json({ error: "provider_unavailable" }, 503);
  const at = new AccessToken(key, secret, { identity: userId, name: profile?.nickname ?? "", ttl: TOKEN_SECONDS });
  at.addGrant({ roomJoin: true, room: callId, canPublish: true, canSubscribe: true, canPublishData: false });
  return json({ provider: "livekit", url, channel: callId, token: await at.toJwt(), expires });
});
