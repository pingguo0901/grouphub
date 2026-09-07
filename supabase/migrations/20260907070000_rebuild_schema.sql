-- =====================================================
-- 集团中台 数据库重建（按董事长 21 张表方案）
-- 红线：报税逻辑完全不读取 owner_drawing
-- =====================================================

-- ---------- 删除旧表 ----------
drop table if exists public.audit_logs cascade;
drop table if exists public.revenue_records cascade;
drop table if exists public.expense_records cascade;
drop table if exists public.owner_drawing cascade;
drop table if exists public.business_entities cascade;

-- =====================================================
-- 1. business_entity 业务主体表
-- =====================================================
create table public.business_entity (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  ssm_no text,
  tin_no text,
  sst_no text,
  business_type text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 2. sales_invoice_summary 销项发票台账
-- =====================================================
create table public.sales_invoice_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 3. purchase_invoice_summary 采购进货进项台账
-- =====================================================
create table public.purchase_invoice_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 4. cashflow_business 企业合规业务收支总账（报税口径，永不存提款）
-- =====================================================
create table public.cashflow_business (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 5. owner_drawing 东主提款记录表（报税逻辑完全不碰本表）
-- =====================================================
create table public.owner_drawing (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  draw_month text,
  amount_myr numeric(14,2) not null default 0,
  bank_ref text,
  note text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 6. inventory_summary 进销存库存汇总
-- =====================================================
create table public.inventory_summary (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 7. staff_payroll 员工薪酬人力成本台账
-- =====================================================
create table public.staff_payroll (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  payroll_month text,
  staff_name text,
  basic_salary numeric(14,2) not null default 0,
  epf_amount numeric(14,2) not null default 0,
  socso_amount numeric(14,2) not null default 0,
  pcb_amount numeric(14,2) not null default 0,
  net_salary numeric(14,2) not null default 0,
  total_cost numeric(14,2) not null default 0,
  external_payroll_id text,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 8. asset_depreciation 固定资产&折旧台账
-- =====================================================
create table public.asset_depreciation (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 9. sst_return SST-02 报税底稿表
-- =====================================================
create table public.sst_return (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
-- 10. formb_tax Form-B 合并报税台账
-- =====================================================
create table public.formb_tax (
  id uuid primary key default gen_random_uuid(),
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
-- 11. tax_alert 税务&业务风险告警表
-- =====================================================
create table public.tax_alert (
  id uuid primary key default gen_random_uuid(),
  level text,
  alert_type text,
  business_entity_id uuid references public.business_entity(id),
  related_doc_id text,
  description text,
  status text not null default '待处理',
  handle_note text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 12. phone_msg_sync 手机消息同步归档表
-- =====================================================
create table public.phone_msg_sync (
  id uuid primary key default gen_random_uuid(),
  source text,
  msg_time timestamptz,
  sender text,
  summary text,
  attachment_path text,
  ai_category text,
  is_linked boolean not null default false,
  linked_doc_id text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 13. biz_app_sync 业务 APP 同步状态记录表
-- =====================================================
create table public.biz_app_sync (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  sync_type text,
  last_success_at timestamptz,
  status text,
  error_msg text,
  row_count int not null default 0
);

-- =====================================================
-- 14. ai_chat_session AI 会话表
-- =====================================================
create table public.ai_chat_session (
  id uuid primary key default gen_random_uuid(),
  session_name text,
  business_entity_id uuid references public.business_entity(id),
  created_at timestamptz not null default now()
);

-- =====================================================
-- 20. audit_log 全局审计日志表（最重要，禁删除）——提前创建供外键引用
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
-- 15. ai_chat_message AI 对话消息明细
-- =====================================================
create table public.ai_chat_message (
  id uuid primary key default gen_random_uuid(),
  ai_chat_session_id uuid not null references public.ai_chat_session(id),
  role text,
  content text,
  model text,
  audit_log_id uuid references public.audit_log(id),
  created_at timestamptz not null default now()
);

-- =====================================================
-- 16. fb_page Facebook 主页绑定配置表（token 加密存储）
-- =====================================================
create table public.fb_page (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  fb_page_id text,
  page_name text,
  page_access_token text,
  followers_count int,
  last_synced_at timestamptz
);

-- =====================================================
-- 17. fb_post_draft Facebook 帖子草稿表
-- =====================================================
create table public.fb_post_draft (
  id uuid primary key default gen_random_uuid(),
  fb_page_id uuid not null references public.fb_page(id),
  content text,
  image_path text,
  scheduled_at timestamptz,
  status text not null default '草稿',
  source text,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 18. fb_post_history 已发布帖子同步摘要
-- =====================================================
create table public.fb_post_history (
  id uuid primary key default gen_random_uuid(),
  fb_page_id uuid not null references public.fb_page(id),
  fb_post_id text,
  content_summary text,
  published_at timestamptz,
  metrics jsonb,
  created_at timestamptz not null default now()
);

-- =====================================================
-- 19. fb_comment_sync FB 评论留言归档表
-- =====================================================
create table public.fb_comment_sync (
  id uuid primary key default gen_random_uuid(),
  fb_post_history_id uuid not null references public.fb_post_history(id),
  fb_comment_id text,
  commenter_name text,
  content text,
  ai_reply_draft text,
  is_replied boolean not null default false,
  synced_at timestamptz not null default now()
);

-- =====================================================
-- 21. approval_record 审批记录表
-- =====================================================
create table public.approval_record (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
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
create index idx_tax_alert_status on public.tax_alert(status);
create index idx_phone_msg_sync_time on public.phone_msg_sync(msg_time);
create index idx_ai_message_session on public.ai_chat_message(ai_chat_session_id);
create index idx_audit_log_time on public.audit_log(created_at);

-- =====================================================
-- RLS：service_role 可读写（绕过 RLS）；authenticated 只读
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
alter table public.tax_alert enable row level security;
alter table public.phone_msg_sync enable row level security;
alter table public.biz_app_sync enable row level security;
alter table public.ai_chat_session enable row level security;
alter table public.ai_chat_message enable row level security;
alter table public.fb_page enable row level security;
alter table public.fb_post_draft enable row level security;
alter table public.fb_post_history enable row level security;
alter table public.fb_comment_sync enable row level security;
alter table public.audit_log enable row level security;
alter table public.approval_record enable row level security;

create policy "read_business_entity" on public.business_entity for select to authenticated using (true);
create policy "read_sales_invoice_summary" on public.sales_invoice_summary for select to authenticated using (true);
create policy "read_purchase_invoice_summary" on public.purchase_invoice_summary for select to authenticated using (true);
create policy "read_cashflow_business" on public.cashflow_business for select to authenticated using (true);
create policy "read_owner_drawing" on public.owner_drawing for select to authenticated using (true);
create policy "read_inventory_summary" on public.inventory_summary for select to authenticated using (true);
create policy "read_staff_payroll" on public.staff_payroll for select to authenticated using (true);
create policy "read_asset_depreciation" on public.asset_depreciation for select to authenticated using (true);
create policy "read_sst_return" on public.sst_return for select to authenticated using (true);
create policy "read_formb_tax" on public.formb_tax for select to authenticated using (true);
create policy "read_tax_alert" on public.tax_alert for select to authenticated using (true);
create policy "read_phone_msg_sync" on public.phone_msg_sync for select to authenticated using (true);
create policy "read_biz_app_sync" on public.biz_app_sync for select to authenticated using (true);
create policy "read_ai_chat_session" on public.ai_chat_session for select to authenticated using (true);
create policy "read_ai_chat_message" on public.ai_chat_message for select to authenticated using (true);
create policy "read_fb_page" on public.fb_page for select to authenticated using (true);
create policy "read_fb_post_draft" on public.fb_post_draft for select to authenticated using (true);
create policy "read_fb_post_history" on public.fb_post_history for select to authenticated using (true);
create policy "read_fb_comment_sync" on public.fb_comment_sync for select to authenticated using (true);
create policy "read_audit_log" on public.audit_log for select to authenticated using (true);
create policy "read_approval_record" on public.approval_record for select to authenticated using (true);

-- =====================================================
-- audit_log 禁止删除（任何角色，含 service_role 也被 trigger 拦截）
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
