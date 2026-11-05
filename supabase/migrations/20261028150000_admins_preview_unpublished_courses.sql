-- Applied via MCP. Admins can read unpublished courses / draft lessons in the app to test them before publishing.
create policy "admins preview unpublished courses" on public.courses
  for select to authenticated using (public.is_community_admin());
create policy "admins preview unpublished books" on public.course_books
  for select to authenticated using (public.is_community_admin());
create policy "admins preview draft lessons" on public.course_lessons
  for select to authenticated using (public.is_community_admin());
