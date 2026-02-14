-- Seed: web3_treasury_keys
-- 참고: 플레이스홀더를 치환하지 않으면 guard 조건이 false가 되어 no-op입니다.

update web3_treasury_keys
set treasury_address = '0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2',
    treasury_private_key_encrypted = 'y7ozvW3IQVopMfMq.1f2Ld2cGv/1M14tmGVuZlapftc2VZC8Md6HTOvHiQZrioP5QreGjW5j+xq1g0C9Akxj+DfbCpxwYT+7C9QqZaFy/86lGfzUbGiz5ZPzR9o8=',
    updated_at = current_timestamp
where id = 1
  and '0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2' like '0x%'
  and length('0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2') = 42
  and position('.' in 'y7ozvW3IQVopMfMq.1f2Ld2cGv/1M14tmGVuZlapftc2VZC8Md6HTOvHiQZrioP5QreGjW5j+xq1g0C9Akxj+DfbCpxwYT+7C9QqZaFy/86lGfzUbGiz5ZPzR9o8=') > 1;

insert into web3_treasury_keys (
    id,
    treasury_address,
    treasury_private_key_encrypted,
    created_at,
    updated_at
)
select
    1,
    '0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2',
    'y7ozvW3IQVopMfMq.1f2Ld2cGv/1M14tmGVuZlapftc2VZC8Md6HTOvHiQZrioP5QreGjW5j+xq1g0C9Akxj+DfbCpxwYT+7C9QqZaFy/86lGfzUbGiz5ZPzR9o8=',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from web3_treasury_keys
    where id = 1
)
  and '0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2' like '0x%'
  and length('0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2') = 42
  and position('.' in 'y7ozvW3IQVopMfMq.1f2Ld2cGv/1M14tmGVuZlapftc2VZC8Md6HTOvHiQZrioP5QreGjW5j+xq1g0C9Akxj+DfbCpxwYT+7C9QqZaFy/86lGfzUbGiz5ZPzR9o8=') > 1;
