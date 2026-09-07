-- =====================================================
-- 第二期（V1.1）表：手机消息归集 / AI 经营助手 / Facebook 营销中控
-- =====================================================

-- 12. phone_msg_sync 手机消息同步归档表（WA/WeChat/Gmail）
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

-- 14. ai_chat_session OpenClaw AI 会话表
create table public.ai_chat_session (
  id uuid primary key default gen_random_uuid(),
  session_name text,
  business_entity_id uuid references public.business_entity(id),
  created_at timestamptz not null default now()
);

-- 15. ai_chat_message AI 对话消息明细
create table public.ai_chat_message (
  id uuid primary key default gen_random_uuid(),
  ai_chat_session_id uuid not null references public.ai_chat_session(id),
  role text,
  content text,
  model text,
  audit_log_id uuid references public.audit_log(id),
  created_at timestamptz not null default now()
);

-- 16. fb_page Facebook 主页绑定配置表（token 加密存储）
create table public.fb_page (
  id uuid primary key default gen_random_uuid(),
  business_entity_id uuid not null references public.business_entity(id),
  fb_page_id text,
  page_name text,
  page_access_token text,
  followers_count int,
  last_synced_at timestamptz
);

-- 17. fb_post_draft Facebook 帖子草稿表
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

-- 18. fb_post_history 已发布帖子同步摘要
create table public.fb_post_history (
  id uuid primary key default gen_random_uuid(),
  fb_page_id uuid not null references public.fb_page(id),
  fb_post_id text,
  content_summary text,
  published_at timestamptz,
  metrics jsonb,
  created_at timestamptz not null default now()
);

-- 19. fb_comment_sync FB 评论留言归档表
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

-- 索引
create index idx_phone_msg_time on public.phone_msg_sync(msg_time);
create index idx_ai_message_session on public.ai_chat_message(ai_chat_session_id);
create index idx_fb_post_draft_page on public.fb_post_draft(fb_page_id);
create index idx_fb_post_history_page on public.fb_post_history(fb_page_id);
create index idx_fb_comment_post on public.fb_comment_sync(fb_post_history_id);

-- =====================================================
-- RLS：service_role 可读写；authenticated 只读
-- =====================================================
alter table public.phone_msg_sync enable row level security;
alter table public.ai_chat_session enable row level security;
alter table public.ai_chat_message enable row level security;
alter table public.fb_page enable row level security;
alter table public.fb_post_draft enable row level security;
alter table public.fb_post_history enable row level security;
alter table public.fb_comment_sync enable row level security;

create policy "read_phone_msg_sync" on public.phone_msg_sync for select to authenticated using (true);
create policy "read_ai_chat_session" on public.ai_chat_session for select to authenticated using (true);
create policy "read_ai_chat_message" on public.ai_chat_message for select to authenticated using (true);
create policy "read_fb_page" on public.fb_page for select to authenticated using (true);
create policy "read_fb_post_draft" on public.fb_post_draft for select to authenticated using (true);
create policy "read_fb_post_history" on public.fb_post_history for select to authenticated using (true);
create policy "read_fb_comment_sync" on public.fb_comment_sync for select to authenticated using (true);
