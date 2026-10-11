-- Applied via MCP. The chat-list preview used to keep showing deleted/edited messages.
create or replace function public.refresh_room_preview(p_room uuid) returns void
language sql security definer set search_path = '' as $$
  update public.rooms r
     set last_message_at = coalesce((select created_at from public.room_messages where room_id = p_room order by created_at desc limit 1), r.created_at),
         last_message_preview = coalesce((select case when kind = 'voice' then '🎤 Voice message' else left(body, 140) end
                                            from public.room_messages where room_id = p_room order by created_at desc limit 1), '')
   where r.id = p_room;
$$;
revoke execute on function public.refresh_room_preview(uuid) from public, anon, authenticated;

create or replace function public.on_room_message_changed() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_room uuid := coalesce(old.room_id, new.room_id);
begin
  -- Only the newest message feeds the preview.
  if old.created_at >= (select last_message_at from public.rooms where id = v_room) then
    perform public.refresh_room_preview(v_room);
  end if;
  return null;
end $$;

create trigger trg_room_message_preview
  after delete or update of body on public.room_messages
  for each row execute function public.on_room_message_changed();

select public.refresh_room_preview(id) from public.rooms;
