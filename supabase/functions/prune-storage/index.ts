// Deletes the oldest voice clips when the voice-recordings bucket nears its cap,
// and avatars whose owner deleted their account.
// Called every 15 minutes by pg_cron (job 'prune-voice-storage'), authenticated
// with a shared secret stored in private.app_secrets. Deployed with verify_jwt = false.
import { createClient } from "npm:@supabase/supabase-js@2";

const BUCKET = "voice-recordings";
const BATCH = 100;

Deno.serve(async (req: Request) => {
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  const secret = req.headers.get("x-cleanup-secret") ?? "";
  const { data: ok, error: authError } = await admin.rpc("verify_cleanup_secret", { p_secret: secret });
  if (authError || ok !== true) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });
  }

  const { data: names, error } = await admin.rpc("voice_prune_candidates");
  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  const paths: string[] = [...new Set((names ?? []) as string[])];
  let removed = 0;
  let expired = 0;
  for (let i = 0; i < paths.length; i += BATCH) {
    const chunk = paths.slice(i, i + BATCH);
    const { data, error: removeError } = await admin.storage.from(BUCKET).remove(chunk);
    if (removeError) {
      return new Response(JSON.stringify({ error: removeError.message, removed, expired }), { status: 500 });
    }
    removed += data?.length ?? 0;
    const { data: count } = await admin.rpc("mark_voice_expired", { p_paths: chunk });
    expired += (count as number) ?? 0;
  }

  let avatars = 0;
  const { data: orphanAvatars } = await admin.rpc("avatar_prune_candidates");
  const avatarPaths = (orphanAvatars ?? []) as string[];
  for (let i = 0; i < avatarPaths.length; i += BATCH) {
    const { data } = await admin.storage.from("avatars").remove(avatarPaths.slice(i, i + BATCH));
    avatars += data?.length ?? 0;
  }

  return new Response(JSON.stringify({ candidates: paths.length, removed, expired, avatars }), {
    headers: { "Content-Type": "application/json" },
  });
});
