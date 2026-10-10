-- 백로그 062: one row per provider attempt made by the ai-learning Edge Function.
-- Written and read only by the function with the service role; RLS is on with no policy, so
-- the anon and authenticated roles (and therefore the app) can neither read nor write it.

create table if not exists public.ai_usage (
  id bigint generated always as identity primary key,
  called_at timestamptz not null default now(),
  -- The learner's calendar day in Asia/Seoul, as the function computes it; the daily limit counts this.
  day date not null,
  action text not null,
  model text not null,
  input_tokens integer not null default 0,
  output_tokens integer not null default 0,
  cache_read_tokens integer not null default 0,
  cache_write_tokens integer not null default 0,
  duration_ms integer not null default 0,
  ok boolean not null,
  error_code text
);

comment on table public.ai_usage is 'ai-learning Edge Function: one row per Anthropic attempt (백로그 062)';

-- The limit check is "rows for today"; the monthly report groups by month and model.
create index if not exists ai_usage_day_idx on public.ai_usage (day);

alter table public.ai_usage enable row level security;

-- Monthly totals for the cost calculation. security_invoker keeps the view under the table's
-- RLS, so only the service role sees it.
create or replace view public.ai_usage_monthly
with (security_invoker = true) as
select
  to_char(day, 'YYYY-MM') as month,
  model,
  count(*) as calls,
  count(*) filter (where not ok) as failed_calls,
  sum(input_tokens) as input_tokens,
  sum(output_tokens) as output_tokens,
  sum(cache_read_tokens) as cache_read_tokens,
  sum(cache_write_tokens) as cache_write_tokens,
  round(avg(duration_ms)) as avg_duration_ms
from public.ai_usage
group by 1, 2
order by 1 desc, 2;
