-- Seed: level_policies
--
-- This script is idempotent (INSERT ... WHERE NOT EXISTS) and is intended to be executed manually
-- (or by tests via @Sql) until a proper migration tool (Flyway/Liquibase) is adopted.

insert into level_policies (
  level,
  required_xp,
  reward_mztk,
  effective_from,
  effective_to,
  enabled,
  created_at
)
select
  1, 300, 20,
  timestamp '2000-01-01 00:00:00', null,
  true, current_timestamp
where not exists (
  select 1
  from level_policies
  where level = 1
    and effective_from = timestamp '2000-01-01 00:00:00'
);

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 2, 600, 25, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 2 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 3, 1000, 30, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 3 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 4, 1500, 105, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 4 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 5, 2100, 40, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 5 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 6, 2800, 45, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 6 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 7, 3600, 50, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 7 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 8, 4500, 55, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 8 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 9, 5500, 180, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 9 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 10, 6600, 65, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 10 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 11, 7800, 70, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 11 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 12, 9100, 75, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 12 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 13, 10500, 80, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 13 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 14, 12000, 255, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 14 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 15, 13600, 90, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 15 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 16, 15300, 95, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 16 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 17, 17100, 100, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 17 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 18, 19000, 105, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 18 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 19, 21000, 330, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 19 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 20, 23100, 115, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 20 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 21, 25300, 120, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 21 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 22, 27600, 125, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 22 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 23, 30000, 130, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 23 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 24, 32500, 405, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 24 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 25, 35100, 140, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 25 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 26, 37800, 145, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 26 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 27, 40600, 150, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 27 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 28, 43500, 155, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 28 and effective_from = timestamp '2000-01-01 00:00:00');

insert into level_policies (level, required_xp, reward_mztk, effective_from, effective_to, enabled, created_at)
select 29, 46500, 480, timestamp '2000-01-01 00:00:00', null, true, current_timestamp
where not exists (select 1 from level_policies where level = 29 and effective_from = timestamp '2000-01-01 00:00:00');

