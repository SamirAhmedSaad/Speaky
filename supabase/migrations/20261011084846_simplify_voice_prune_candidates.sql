create or replace function public.voice_prune_candidates() returns setof text
language plpgsql stable security definer set search_path = '' as $$
declare
  v_cap bigint := private.cfg('voice_cap_bytes');
  v_total bigint;
  v_to_free bigint;
begin
  -- Orphaned clips (their message was deleted) older than a day
  return query
    select o.name from storage.objects o
     where o.bucket_id = 'voice-recordings'
       and o.created_at < now() - interval '1 day'
       and not exists (select 1 from public.room_messages m where m.voice_path = o.name);

  select coalesce(sum((o.metadata->>'size')::bigint), 0) into v_total
    from storage.objects o where o.bucket_id = 'voice-recordings';

  if v_total > v_cap * private.cfg('voice_high_pct') / 100 then
    v_to_free := v_total - v_cap * private.cfg('voice_low_pct') / 100;
    -- Oldest first, until the freed bytes reach the target
    return query
      select s.name from (
        select o.name,
               coalesce((o.metadata->>'size')::bigint, 0) as size,
               sum(coalesce((o.metadata->>'size')::bigint, 0))
                 over (order by o.created_at, o.name) as running
          from storage.objects o
         where o.bucket_id = 'voice-recordings'
      ) s
      where s.running - s.size < v_to_free;
  end if;
end $$;
revoke execute on function public.voice_prune_candidates() from public, anon, authenticated;
grant execute on function public.voice_prune_candidates() to service_role;
