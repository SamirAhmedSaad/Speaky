-- Master switch for in-app update prompts (additive, safe). Off by default; admins turn it on from the app.
insert into public.app_settings (key, value) values ('update_prompts_on', 'false'::jsonb)
on conflict (key) do nothing;

create or replace function public.app_update_set_enabled(p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  insert into public.app_settings (key, value) values ('update_prompts_on', to_jsonb(p_on))
  on conflict (key) do update set value = excluded.value, updated_at = now();
end $$;

revoke all on function public.app_update_set_enabled(boolean) from public, anon;
grant execute on function public.app_update_set_enabled(boolean) to authenticated;
