-- =====================================================
-- 用户角色权限：区分老板(admin) / 秘书(secretary)
-- 老板可看 owner_drawing；秘书禁看
-- =====================================================

-- 用户角色表
create table public.user_roles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  role text not null,
  created_at timestamptz not null default now()
);

alter table public.user_roles enable row level security;

-- 用户只能读自己的角色记录
create policy "read_own_role" on public.user_roles
  for select to authenticated
  using (user_id = auth.uid());

-- owner_drawing：仅 admin（老板）可读，秘书看不到
create policy "owner_drawing_admin_read" on public.owner_drawing
  for select to authenticated
  using (
    exists (
      select 1 from public.user_roles
      where user_id = auth.uid() and role = 'admin'
    )
  );
