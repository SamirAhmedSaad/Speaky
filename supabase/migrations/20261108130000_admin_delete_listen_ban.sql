-- Admin tools: delete any message (Hub or private chat), play voice messages in the private-chat
-- viewer, and ban someone directly from their profile (admin_ban already exists; no report needed).

-- Delete anyone's message. Its voice clip (if any) is removed by the prune-voice-storage job.
create or replace function public.admin_delete_message(p_message uuid)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  delete from public.room_messages where id = p_message;
end $$;
revoke all on function public.admin_delete_message(uuid) from public, anon;
grant execute on function public.admin_delete_message(uuid) to authenticated;

-- Private-chat viewer with the voice clip path (admin_chat_messages stays for older app versions).
create or replace function public.admin_chat_messages_v2(p_room uuid, p_limit int default 50, p_before timestamptz default null)
returns table (id uuid, sender_id uuid, kind text, body text, voice_path text, voice_duration_ms int,
               voice_waveform text, created_at timestamptz, edited_at timestamptz)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  if (select r.kind from public.rooms r where r.id = p_room) is distinct from 'direct' then raise exception 'not allowed'; end if;
  return query
  select m.id, m.sender_id, m.kind, m.body, m.voice_path, m.voice_duration_ms, m.voice_waveform, m.created_at, m.edited_at
    from public.room_messages m
   where m.room_id = p_room and (p_before is null or m.created_at < p_before)
   order by m.created_at desc
   limit least(greatest(p_limit, 1), 200);
end $$;
revoke all on function public.admin_chat_messages_v2(uuid, int, timestamptz) from public, anon;
grant execute on function public.admin_chat_messages_v2(uuid, int, timestamptz) to authenticated;

-- Admins can download any voice clip (to listen in the private-chat viewer).
create policy "Admins read voice clips" on storage.objects for select to authenticated
  using (bucket_id = 'voice-recordings' and public.is_community_admin());
