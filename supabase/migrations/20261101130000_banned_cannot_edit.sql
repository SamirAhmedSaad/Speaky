-- A banned user could still edit their old messages; editing now needs the same "not banned" check as posting.
drop policy if exists "Edit your own text messages" on public.room_messages;
create policy "Edit your own text messages" on public.room_messages for update
  using ((sender_id = (select auth.uid())) and (kind = 'text') and not public.is_banned((select auth.uid())))
  with check ((sender_id = (select auth.uid())) and (kind = 'text') and not public.is_banned((select auth.uid())));
