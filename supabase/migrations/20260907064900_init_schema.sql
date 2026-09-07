-- =====================================================
-- 集团中台 初始表结构 + RLS
-- 红线提醒：owner_drawing 表为「个人提款」，报税相关计算必须完全不读取此表
-- =====================================================

-- 1. 主体表（两家公司：炙巷食谱、星域臻旅，马来西亚 Enterprise/ROB）
create table public.business_entities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  entity_type text not null default 'Enterprise',
  registration_no text,
  sst_no text,
  country text not null default 'Malaysia',
  fiscal_year_start date,
  fiscal_year_end date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2. 营收流水（汇总自两家源项目）
create table public.revenue_records (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid not null references public.business_entities(id),
  revenue_date date not null,
  amount numeric(14,2) not null default 0,
  channel text,
  source_ref text,
  description text,
  synced_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

-- 3. 成本/支出（含报税可抵扣标记）
create table public.expense_records (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid not null references public.business_entities(id),
  expense_date date not null,
  amount numeric(14,2) not null default 0,
  category text,
  deductible boolean not null default true,
  has_receipt boolean not null default false,
  source_ref text,
  description text,
  synced_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

-- 4. 个人提款（⚠️ 红线：报税计算完全不读取此表）
create table public.owner_drawing (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid not null references public.business_entities(id),
  draw_date date not null,
  amount numeric(14,2) not null default 0,
  note text,
  created_at timestamptz not null default now()
);

-- 5. 审计日志（全部关键操作记录）
create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid references public.business_entities(id),
  action text not null,
  table_name text,
  record_id text,
  actor text not null default 'service_role',
  detail jsonb,
  created_at timestamptz not null default now()
);

-- 索引
create index idx_revenue_entity_date on public.revenue_records(entity_id, revenue_date);
create index idx_expense_entity_date on public.expense_records(entity_id, expense_date);
create index idx_owner_drawing_entity_date on public.owner_drawing(entity_id, draw_date);
create index idx_audit_logs_created_at on public.audit_logs(created_at);

-- ============ RLS ============
-- service_role 默认绕过 RLS（同步层 / 我可读写）
-- authenticated（秘书等登录用户）只读

alter table public.business_entities enable row level security;
alter table public.revenue_records enable row level security;
alter table public.expense_records enable row level security;
alter table public.owner_drawing enable row level security;
alter table public.audit_logs enable row level security;

create policy "read_business_entities" on public.business_entities for select to authenticated using (true);
create policy "read_revenue_records" on public.revenue_records for select to authenticated using (true);
create policy "read_expense_records" on public.expense_records for select to authenticated using (true);
create policy "read_owner_drawing" on public.owner_drawing for select to authenticated using (true);
create policy "read_audit_logs" on public.audit_logs for select to authenticated using (true);
