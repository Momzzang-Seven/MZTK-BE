-- Seed: xp_policies (from Excel: docs/[몸짱코인] 통합 기획 시트.xlsx / sheet: 보상정책)
--
-- This script is idempotent (INSERT ... WHERE NOT EXISTS) and is intended to be executed manually
-- (or by tests via @Sql) until a proper migration tool (Flyway/Liquibase) is adopted.

insert into xp_policies (
  type,
  xp_amount,
  daily_cap,
  effective_from,
  effective_to,
  enabled,
  created_at
)
select
  'CHECK_IN', 10, 1,
  timestamp '2000-01-01 00:00:00', null,
  true, current_timestamp
where not exists (
  select 1
  from xp_policies
  where type = 'CHECK_IN'
    and effective_from = timestamp '2000-01-01 00:00:00'
);

insert into xp_policies (type, xp_amount, daily_cap, effective_from, effective_to, enabled, created_at)
select 'STREAK_7D', 100, 1, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from xp_policies where type = 'STREAK_7D' and effective_from = timestamp '2000-01-01 00:00:00');

insert into xp_policies (type, xp_amount, daily_cap, effective_from, effective_to, enabled, created_at)
select 'WORKOUT', 100, 1, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from xp_policies where type = 'WORKOUT' and effective_from = timestamp '2000-01-01 00:00:00');

insert into xp_policies (type, xp_amount, daily_cap, effective_from, effective_to, enabled, created_at)
select 'POST', 30, 5, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from xp_policies where type = 'POST' and effective_from = timestamp '2000-01-01 00:00:00');

insert into xp_policies (type, xp_amount, daily_cap, effective_from, effective_to, enabled, created_at)
select 'COMMENT', 1, -1, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from xp_policies where type = 'COMMENT' and effective_from = timestamp '2000-01-01 00:00:00');

