-- ============================================================
-- V003: Insert Initial XP Policies (XP 정책 초기 데이터)
-- ============================================================
-- Purpose: 출석, 운동, 게시글 등의 XP 지급 정책 설정
-- Idempotent: WHERE NOT EXISTS로 중복 방지
-- ============================================================

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

