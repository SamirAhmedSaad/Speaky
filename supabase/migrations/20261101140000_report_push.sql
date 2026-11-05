-- Push to admins/moderators when someone reports a user.
create or replace function public.report_push_recipients(p_report uuid)
returns table(token text, user_id uuid, reporter_nickname text, reported_nickname text, reason text)
language sql stable security definer
set search_path = ''
as $$
  select t.token, t.user_id, coalesce(rep.nickname, 'Someone'), coalesce(tgt.nickname, 'someone'), r.reason
    from public.user_reports r
    join private.community_admins a on a.user_id <> r.reporter_id
    join public.device_tokens t on t.user_id = a.user_id
    left join public.community_users rep on rep.id = r.reporter_id
    left join public.community_users tgt on tgt.id = r.reported_user_id
   where r.id = p_report
$$;
revoke all on function public.report_push_recipients(uuid) from public, anon, authenticated;
grant execute on function public.report_push_recipients(uuid) to service_role;

create or replace function public.on_report_push()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  perform net.http_post(
    url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/report-notify',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
    body := jsonb_build_object('report_id', new.id),
    timeout_milliseconds := 5000);
  return new;
end $$;

create trigger trg_report_push after insert on public.user_reports
  for each row execute function public.on_report_push();
