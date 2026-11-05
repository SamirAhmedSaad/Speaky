-- Apps from build 48 send their build number with the push token; older apps leave it null.
-- Voice call invitations only go to builds that have voice calls (48+).
alter table public.device_tokens add column if not exists app_version int;

create or replace function public.call_push_recipients(p_call uuid)
returns table(token text, user_id uuid, starter_nickname text, room_id uuid, room_name text)
language sql stable security definer
set search_path = ''
as $$
  select t.token, t.user_id, coalesce(s.nickname, 'Someone'), c.room_id, coalesce(r.name, 'Speaky Hub')
    from public.voice_calls c
    join public.rooms r on r.id = c.room_id
    left join public.community_users s on s.id = c.started_by
    join public.community_users cu on cu.id <> c.started_by and cu.hub_notify
    join public.device_tokens t on t.user_id = cu.id and coalesce(t.app_version, 0) >= 48
   where c.id = p_call
     and c.ended_at is null
     and not public.is_banned(cu.id)
     and not private.voice_banned(cu.id)
$$;
