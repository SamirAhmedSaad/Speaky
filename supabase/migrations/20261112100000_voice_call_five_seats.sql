-- Hub voice calls: 5 regular seats (was 10). Premium members keep their 5 extra seats.
-- Same function signature, only the regular-seat limit changes. People already in a call stay;
-- only new joins see the new limit. Older app versions just get "The call is full." sooner.

create or replace function private.voice_take_seat(p_call uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := (select auth.uid());
  v_call public.voice_calls;
  v_count int;
  v_premium_count int;
  v_premium boolean;
  v_seat boolean := false;
begin
  select * into v_call from public.voice_calls where id = p_call and ended_at is null for update;
  if v_call.id is null then raise exception 'ended'; end if;
  if exists (select 1 from public.voice_call_members where call_id = p_call and user_id = v_me) then
    update public.voice_call_members set last_seen = now() where call_id = p_call and user_id = v_me;
  else
    select count(*), count(*) filter (where premium_seat) into v_count, v_premium_count
      from public.voice_call_members where call_id = p_call;
    if v_count - v_premium_count >= 5 then
      select public.is_premium(u) into v_premium from public.community_users u where u.id = v_me;
      if not coalesce(v_premium, false) then raise exception 'full'; end if;
      if v_premium_count >= 5 then raise exception 'premium_full'; end if;
      v_seat := true;
    end if;
    insert into public.voice_call_members (call_id, user_id, premium_seat) values (p_call, v_me, v_seat);
    update public.voice_calls
       set peak_members = greatest(peak_members, (select count(*) from public.voice_call_members where call_id = p_call))
     where id = p_call;
  end if;
  return jsonb_build_object('call_id', p_call, 'provider', v_call.provider, 'premium_seat', v_seat);
end $$;
