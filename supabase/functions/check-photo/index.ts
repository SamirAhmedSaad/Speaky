// Checks profile photos with Google Cloud Vision SafeSearch and removes immodest ones.
// Called by the trg_photo_check trigger ({user_id, url}) and the daily cron ({scan: true}), with the
// shared push secret. Deployed with verify_jwt = false.
// Auth to Vision: GOOGLE_VISION_API_KEY if set, else the FCM_SERVICE_ACCOUNT (cloud-vision scope).
// Never blocks anything on failure: errors and an exhausted quota leave the photo "unchecked".
import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";

const LEVEL: Record<string, number> = { UNKNOWN: 0, VERY_UNLIKELY: 1, UNLIKELY: 2, POSSIBLE: 3, LIKELY: 4, VERY_LIKELY: 5 };
// Block from this likelihood up (POSSIBLE = 3, LIKELY = 4). "racy" = revealing / swimwear / lingerie.
const ADULT_MIN = Number(Deno.env.get("PHOTO_ADULT_MIN") ?? 3);
const RACY_MIN = Number(Deno.env.get("PHOTO_RACY_MIN") ?? 4);
const VIOLENCE_MIN = Number(Deno.env.get("PHOTO_VIOLENCE_MIN") ?? 4);
const MONTHLY_LIMIT = Number(Deno.env.get("VISION_MONTHLY_LIMIT") ?? 950);

type ServiceAccount = { client_email: string; private_key: string };
let cachedToken: { token: string; until: number } | null = null;

async function serviceAccountToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.until > now + 60) return cachedToken.token;
  const key = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/cloud-vision" })
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
  cachedToken = { token: json.access_token, until: now + 3500 };
  return json.access_token;
}

async function safeSearch(content: string) {
  const apiKey = Deno.env.get("GOOGLE_VISION_API_KEY");
  let url = "https://vision.googleapis.com/v1/images:annotate";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (apiKey) {
    url += `?key=${apiKey}`;
  } else {
    const sa = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!sa) throw new Error("No GOOGLE_VISION_API_KEY or FCM_SERVICE_ACCOUNT");
    headers.Authorization = `Bearer ${await serviceAccountToken(JSON.parse(sa))}`;
  }
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify({ requests: [{ image: { content }, features: [{ type: "SAFE_SEARCH_DETECTION" }] }] }),
  });
  const json = await res.json();
  const r = json?.responses?.[0];
  if (!res.ok || r?.error || !r?.safeSearchAnnotation) throw new Error(`Vision error: ${JSON.stringify(json).slice(0, 300)}`);
  return r.safeSearchAnnotation as Record<string, string>;
}

async function checkOne(admin: SupabaseClient, userId: string, photoUrl: string) {
  const result = async (status: string, reason: string | null) => {
    await admin.rpc("set_photo_result", { p_user: userId, p_url: photoUrl, p_status: status, p_reason: reason });
    return { userId, status, reason };
  };
  try {
    const img = await fetch(photoUrl, { signal: AbortSignal.timeout(10_000), headers: { "User-Agent": "SpeakyPhotoCheck/1.0 (profile photo moderation)" } });
    if (!img.ok) return result("unchecked", `fetch ${img.status}`);
    const bytes = new Uint8Array(await img.arrayBuffer());
    // Only real checks use the monthly allowance.
    const { data: allowed } = await admin.rpc("vision_take_quota", { p_limit: MONTHLY_LIMIT });
    if (allowed !== true) return result("unchecked", "quota");
    const s = await safeSearch(encodeBase64(bytes));
    const adult = LEVEL[s.adult] ?? 0, racy = LEVEL[s.racy] ?? 0, violence = LEVEL[s.violence] ?? 0;
    if (adult >= ADULT_MIN) return result("rejected", "adult");
    if (racy >= RACY_MIN) return result("rejected", "racy");
    if (violence >= VIOLENCE_MIN) return result("rejected", "violence");
    return result("ok", null);
  } catch (e) {
    console.error("check failed", userId, String(e));
    return result("unchecked", "error");
  }
}

Deno.serve(async (req: Request) => {
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const secret = req.headers.get("x-push-secret") ?? "";
  const { data: ok } = await admin.rpc("verify_push_secret", { p_secret: secret });
  if (ok !== true) return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });

  const body = await req.json().catch(() => ({}));
  if (body.user_id && body.url) {
    const r = await checkOne(admin, body.user_id, body.url);
    return new Response(JSON.stringify(r), { headers: { "Content-Type": "application/json" } });
  }
  if (body.scan) {
    const limit = Math.min(Number(body.limit ?? 60), 200);
    const { data: rows } = await admin.from("community_users").select("id, photo_url")
      .in("photo_status", ["unchecked", "pending"]).not("photo_url", "is", null).limit(limit);
    const results = [];
    for (const row of rows ?? []) {
      if (!row.photo_url) continue;
      const r = await checkOne(admin, row.id, row.photo_url);
      results.push(r);
      if (r.reason === "quota") break;
    }
    return new Response(JSON.stringify({ checked: results.length, results }), { headers: { "Content-Type": "application/json" } });
  }
  return new Response(JSON.stringify({ error: "bad request" }), { status: 400 });
});
