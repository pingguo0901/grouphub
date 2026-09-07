-- =====================================================
-- 集团双企业 0到1 终版方案 · 数据库重建 V2
-- 双账本隔离：account_type = official(报税) / private(真实经营)
-- 行业：business_industry = F&B / ElectronicTrade
-- =====================================================

-- ---------- 删除旧表（21张）----------
drop table if exists public.fb_comment_sync cascade;
drop table if exists public.fb_post_history cascade;
drop table if exists public.fb_post_draft cascade;
drop table if exists public.fb_page cascade;
drop table if exists public.ai_chat_message cascade;
drop table if exists public.ai_chat_session cascade;
drop table if exists public.phone_msg_sync cascade;
drop table if exists public.approval_record cascade;
drop table if exists public.audit_log cascade;
drop table if exists public.tax_alert cascade;
drop table if exists public.formb_tax cascade;
drop table if exists public.sst_return cascade;
drop table if exists public.biz_app_sync cascade;
drop table if exists public.asset_depreciation cascade;
drop table if exists public.staff_payroll cascade;
drop table if exists public.inventory_summary cascade;
drop table if exists public.owner_drawing cascade;
drop table if exists public.cashflow_business cascade;
drop table if exists public.purchase_invoice_summary cascade;
drop table if exists public.sales_invoice_summary cascade;
drop table if exists public.business_entity cascade;

-- =====================================================
-- 1. business_entity 企业主体表
-- =====================================================
create table public.business_entity (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  ssm_no text,
  tin_no text,
  sst_status text,
  business_industry text,
  account_type text not null default 'official',
  created_at timestamptz not null default now()
);

-- =====================================================
-- 2. sales_invoice_summary 销项发票台账
-- =====================================================
create table public.sales_invoice_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  external_order_id text,
  doc_type text,
  order_date date,
  net_amount numeric(14,2) not null default 0,
  sst_amount numeric(14,2) not null default 0,
  total_amount numeric(14,2) not null default 0,
  myinvois_uuid text,
  invoice_status text,
  customer_info text,
  is_taxable boolean not null default true,
  invoice_pdf_path text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 3. purchase_invoice_summary 采购进货台账
-- =====================================================
create table public.purchase_invoice_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  external_purchase_id text,
  purchase_date date,
  supplier_name text,
  supplier_ssm text,
  supplier_tin text,
  supplier_sst_no text,
  total_amount numeric(14,2) not null default 0,
  input_sst_amount numeric(14,2) not null default 0,
  sst_type text,
  stock_status text,
  payment_ref text,
  receipt_path text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 4. cashflow_business 合规收支总账（报税核心，绝不存老板提款）
-- =====================================================
create table public.cashflow_business (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  flow_date date,
  flow_type text,
  category text,
  amount numeric(14,2) not null default 0,
  deductible boolean not null default true,
  external_doc_id text,
  linked_invoice_id text,
  receipt_path text,
  note text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 5. owner_drawing 东主提款表（仅内部，永不参与报税）
-- =====================================================
create table public.owner_drawing (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'private',
  business_industry text,
  draw_month text,
  amount_myr numeric(14,2) not null default 0,
  bank_ref text,
  note text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 6. inventory_summary 库存台账
-- =====================================================
create table public.inventory_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  item_name text,
  batch_expiry text,
  movement_type text,
  quantity numeric(14,3) not null default 0,
  unit_cost numeric(14,2) not null default 0,
  total_amount numeric(14,2) not null default 0,
  stocktake_flag boolean not null default false,
  external_stock_id text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 7. staff_payroll 薪资总表（完整马来西亚合规）
-- =====================================================
create table public.staff_payroll (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  payroll_month text,
  staff_name text,
  basic_salary numeric(14,2) not null default 0,
  allowance numeric(14,2) not null default 0,
  overtime numeric(14,2) not null default 0,
  bonus numeric(14,2) not null default 0,
  gross_salary numeric(14,2) not null default 0,
  employee_epf numeric(14,2) not null default 0,
  employer_epf numeric(14,2) not null default 0,
  employee_socso numeric(14,2) not null default 0,
  employer_socso numeric(14,2) not null default 0,
  employee_eis numeric(14,2) not null default 0,
  employer_eis numeric(14,2) not null default 0,
  pcb_mtd numeric(14,2) not null default 0,
  net_salary numeric(14,2) not null default 0,
  total_cost numeric(14,2) not null default 0,
  payslip_pdf_path text,
  is_foreigner boolean not null default false,
  external_payroll_id text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 8. asset_depreciation 固定资产台账
-- =====================================================
create table public.asset_depreciation (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  asset_name text,
  purchase_date date,
  original_value numeric(14,2) not null default 0,
  useful_life_years int,
  monthly_depreciation numeric(14,2) not null default 0,
  accumulated_depreciation numeric(14,2) not null default 0,
  capital_allowance text,
  asset_status text,
  receipt_path text,
  updated_at timestamptz not null default now()
);

-- =====================================================
-- 9. sst_return SST02 双月报税底稿
-- =====================================================
create table public.sst_return (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  tax_period text,
  output_sst numeric(14,2) not null default 0,
  input_sst_deductible numeric(14,2) not null default 0,
  sst_payable numeric(14,2) not null default 0,
  draft_generated_at timestamptz,
  filing_status text,
  receipt_pdf_path text,
  payment_note text
);

-- =====================================================
-- 10. formb_tax FormB 年度生意报税底稿
-- =====================================================
create table public.formb_tax (
  id uuid primary key default gen_random_uuid(),
  account_type text not null default 'official',
  business_industry text,
  tax_year text,
  zhixiang_profit numeric(14,2) not null default 0,
  xyzl_profit numeric(14,2) not null default 0,
  total_taxable_profit numeric(14,2) not null default 0,
  cp500_records jsonb,
  draft_generated_at timestamptz,
  filing_status text,
  doc_path text
);

-- =====================================================
-- 11. forme_submit FormE 年度薪资申报底稿（新增）
-- =====================================================
create table public.forme_submit (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  tax_year text,
  total_epf numeric(14,2) not null default 0,
  total_socso numeric(14,2) not null default 0,
  total_eis numeric(14,2) not null default 0,
  total_pcb numeric(14,2) not null default 0,
  draft_generated_at timestamptz,
  filing_status text,
  doc_path text
);

-- =====================================================
-- 12. tax_alert 税务风险告警
-- =====================================================
create table public.tax_alert (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  level text,
  alert_type text,
  related_doc_id text,
  description text,
  status text not null default '待处理',
  handle_note text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 13. biz_app_sync 业务同步记录表
-- =====================================================
create table public.biz_app_sync (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  sync_type text,
  last_success_at timestamptz,
  status text,
  error_msg text,
  row_count int not null default 0
);

-- =====================================================
-- 14. compliance_reminder 马来西亚法定合规日历提醒（新增）
-- =====================================================
create table public.compliance_reminder (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  reminder_type text,
  due_date date,
  description text,
  status text not null default '待处理',
  created_at timestamptz not null default now()
);

-- =====================================================
-- 15. audit_log 全局审计日志（禁删禁改）
-- =====================================================
create table public.audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_id text,
  action_type text,
  related_table_id text,
  description text,
  ip_info text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 16. approval_record 审批记录表
-- =====================================================
create table public.approval_record (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  account_type text not null default 'official',
  business_industry text,
  approval_type text,
  content text,
  amount numeric(14,2) not null default 0,
  attachment_path text,
  status text not null default '待审批',
  approval_note text,
  audit_log_id uuid references public.audit_log(id),
  created_at timestamptz not null default now()
);

-- =====================================================
-- 索引
-- =====================================================
create index idx_sales_invoice_entity on public.sales_invoice_summary(business_entity_id, order_date);
create index idx_purchase_invoice_entity on public.purchase_invoice_summary(business_entity_id, purchase_date);
create index idx_cashflow_entity on public.cashflow_business(business_entity_id, flow_date);
create index idx_owner_drawing_entity on public.owner_drawing(business_entity_id, draw_month);
create index idx_inventory_entity on public.inventory_summary(business_entity_id);
create index idx_payroll_entity on public.staff_payroll(business_entity_id, payroll_month);
create index idx_asset_entity on public.asset_depreciation(business_entity_id);
create index idx_sst_return_entity on public.sst_return(business_entity_id, tax_period);
create index idx_formb_year on public.formb_tax(tax_year);
create index idx_forme_entity on public.forme_submit(business_entity_id, tax_year);
create index idx_tax_alert_status on public.tax_alert(status);
create index idx_compliance_due on public.compliance_reminder(due_date);
create index idx_audit_log_time on public.audit_log(created_at);

-- =====================================================
-- RLS：service_role 可读写（绕过）；authenticated 只读
-- =====================================================
alter table public.business_entity enable row level security;
alter table public.sales_invoice_summary enable row level security;
alter table public.purchase_invoice_summary enable row level security;
alter table public.cashflow_business enable row level security;
alter table public.owner_drawing enable row level security;
alter table public.inventory_summary enable row level security;
alter table public.staff_payroll enable row level security;
alter table public.asset_depreciation enable row level security;
alter table public.sst_return enable row level security;
alter table public.formb_tax enable row level security;
alter table public.forme_submit enable row level security;
alter table public.tax_alert enable row level security;
alter table public.biz_app_sync enable row level security;
alter table public.compliance_reminder enable row level security;
alter table public.audit_log enable row level security;
alter table public.approval_record enable row level security;

create policy "read_business_entity" on public.business_entity for select to authenticated using (true);
create policy "read_sales_invoice_summary" on public.sales_invoice_summary for select to authenticated using (true);
create policy "read_purchase_invoice_summary" on public.purchase_invoice_summary for select to authenticated using (true);
create policy "read_cashflow_business" on public.cashflow_business for select to authenticated using (true);
create policy "read_inventory_summary" on public.inventory_summary for select to authenticated using (true);
create policy "read_staff_payroll" on public.staff_payroll for select to authenticated using (true);
create policy "read_asset_depreciation" on public.asset_depreciation for select to authenticated using (true);
create policy "read_sst_return" on public.sst_return for select to authenticated using (true);
create policy "read_formb_tax" on public.formb_tax for select to authenticated using (true);
create policy "read_forme_submit" on public.forme_submit for select to authenticated using (true);
create policy "read_tax_alert" on public.tax_alert for select to authenticated using (true);
create policy "read_biz_app_sync" on public.biz_app_sync for select to authenticated using (true);
create policy "read_compliance_reminder" on public.compliance_reminder for select to authenticated using (true);
create policy "read_audit_log" on public.audit_log for select to authenticated using (true);
create policy "read_approval_record" on public.approval_record for select to authenticated using (true);

-- owner_drawing：秘书（authenticated）禁止查看老板提款 → 不给 SELECT 策略

-- =====================================================
-- audit_log 禁止删除 + 禁止修改（任何角色，含 service_role）
-- =====================================================
create or replace function public.prevent_audit_log_delete()
returns trigger as $$
begin
  raise exception 'audit_log 记录禁止删除';
end;
$$ language plpgsql;

create trigger audit_log_no_delete
before delete on public.audit_log
for each row execute function public.prevent_audit_log_delete();

create or replace function public.prevent_audit_log_update()
returns trigger as $$
begin
  raise exception 'audit_log 记录禁止修改';
end;
$$ language plpgsql;

create trigger audit_log_no_update
before update on public.audit_log
for each row execute function public.prevent_audit_log_update();
