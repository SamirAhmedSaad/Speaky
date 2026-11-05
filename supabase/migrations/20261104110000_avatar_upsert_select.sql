-- Profile photo uploads use upsert (avatars/<uid>/avatar.jpg), and Storage needs SELECT on the
-- object for that, not just INSERT/UPDATE: without it every upload failed with an RLS error.
-- The bucket is public, so this only exposes what the public URL already shows.
drop policy if exists "Users read their own avatar" on storage.objects;
create policy "Users read their own avatar" on storage.objects
  for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
