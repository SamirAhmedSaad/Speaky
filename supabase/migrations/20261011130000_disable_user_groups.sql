-- The app now has one general chat (Speaky Hub) plus private messages; no user-created groups.
alter policy "Signed-in users create groups they own" on public.rooms with check (false);
alter policy "Signed-in users join public groups" on public.room_members with check (false);
