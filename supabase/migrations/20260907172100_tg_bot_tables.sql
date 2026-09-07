-- =====================================================
-- Telegram Bot 包车订单模块（星域臻旅专属，全部 account_type=private）
-- 红线：tg_* 表数据绝不参与报税计算，只进真实经营账本
-- =====================================================

-- 1. tg_bot_user Telegram 机器人用户表
create table public.tg_bot_user (
  tg_user_id text primary key,
  user_role text,
  full_name text,
  contact_info text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

-- 2. tg_charter_order Telegram 包车订单主表（核心）
create table public.tg_charter_order (
  id uuid primary key default gen_random_uuid(),
  order_no text not null unique,
  tg_customer_id text,
  tg_agent_id text,
  tg_driver_id text,
  trip_details text,
  order_status text not null default 'new',
  total_order_amount numeric(14,2) not null default 0,
  agent_commission numeric(14,2) not null default 0,
  driver_payable numeric(14,2) not null default 0,
  our_gross_profit numeric(14,2) not null default 0,
  note text,
  account_type text not null default 'private',
  business_industry text not null default 'ElectronicTrade',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 3. tg_order_payment 订单结款 & 收款记录表
create table public.tg_order_payment (
  id uuid primary key default gen_random_uuid(),
  order_no text,
  payment_type text,
  amount numeric(14,2) not null default 0,
  payment_method text,
  payment_status text not null default 'pending',
  proof_file_path text,
  operator_tg_user_id text,
  account_type text not null default 'private',
  created_at timestamptz not null default now()
);

-- 4. tg_bot_message_log Bot 交互日志表（不可删除）
create table public.tg_bot_message_log (
  id uuid primary key default gen_random_uuid(),
  tg_user_id text,
  message_in text,
  message_out text,
  order_no text,
  timestamp timestamptz not null default now()
);

-- 索引
create index idx_tg_order_status on public.tg_charter_order(order_status);
create index idx_tg_order_customer on public.tg_charter_order(tg_customer_id);
create index idx_tg_payment_order on public.tg_order_payment(order_no);
create index idx_tg_log_user on public.tg_bot_message_log(tg_user_id);

-- =====================================================
-- RLS：service_role（Bot 服务端）可读写；authenticated 只读（老板/秘书通过 APP 读，秘书不可改）
-- =====================================================
alter table public.tg_bot_user enable row level security;
alter table public.tg_charter_order enable row level security;
alter table public.tg_order_payment enable row level security;
alter table public.tg_bot_message_log enable row level security;

create policy "read_tg_bot_user" on public.tg_bot_user for select to authenticated using (true);
create policy "read_tg_charter_order" on public.tg_charter_order for select to authenticated using (true);
create policy "read_tg_order_payment" on public.tg_order_payment for select to authenticated using (true);
create policy "read_tg_bot_message_log" on public.tg_bot_message_log for select to authenticated using (true);

-- tg_bot_message_log 禁止删除（Bot 对话日志，排错用）
create or replace function public.prevent_tg_log_delete()
returns trigger as $$
begin
  raise exception 'tg_bot_message_log 记录禁止删除';
end;
$$ language plpgsql;

create trigger tg_log_no_delete
before delete on public.tg_bot_message_log
for each row execute function public.prevent_tg_log_delete();
