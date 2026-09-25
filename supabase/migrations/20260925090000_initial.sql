set search_path = public;

create table if not exists users (
  id uuid primary key, email text not null unique, password_hash text not null,
  username text not null unique, display_name text not null, bio text not null default '',
  created_at timestamptz not null default now()
);
create table if not exists sessions (
  token_hash text primary key, user_id uuid not null references users(id) on delete cascade,
  expires_at timestamptz not null
);
create table if not exists works (
  id uuid primary key, author_id uuid not null references users(id), title text not null,
  description text not null default '', category text not null, tags text[] not null default '{}',
  status text not null default 'published' check (status in ('published','hidden')),
  curated boolean not null default false, current_revision_id uuid,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists revisions (
  id uuid primary key, work_id uuid not null references works(id) on delete cascade,
  code text not null, license text not null check (license in ('MIT','CC0-1.0')),
  parameters jsonb not null default '[]', parent_revision_id uuid references revisions(id),
  preview text, created_at timestamptz not null default now()
);
create table if not exists drafts (
  id uuid primary key, owner_id uuid not null references users(id) on delete cascade,
  version integer not null, body jsonb not null, conflict_of uuid,
  updated_at timestamptz not null default now()
);
create table if not exists likes (user_id uuid references users(id) on delete cascade, work_id uuid references works(id) on delete cascade, primary key(user_id,work_id));
create table if not exists saves (user_id uuid references users(id) on delete cascade, work_id uuid references works(id) on delete cascade, primary key(user_id,work_id));
create table if not exists follows (user_id uuid references users(id) on delete cascade, author_id uuid references users(id) on delete cascade, primary key(user_id,author_id), check(user_id<>author_id));
create table if not exists blocks (user_id uuid references users(id) on delete cascade, author_id uuid references users(id) on delete cascade, primary key(user_id,author_id), check(user_id<>author_id));
create table if not exists comments (
  id uuid primary key, work_id uuid not null references works(id) on delete cascade,
  author_id uuid not null references users(id), body text not null,
  request_id uuid not null, created_at timestamptz not null default now(), unique(author_id,request_id)
);
create table if not exists presets (
  user_id uuid not null references users(id) on delete cascade, revision_id uuid not null references revisions(id) on delete cascade,
  vals jsonb not null, primary key(user_id,revision_id)
);
create table if not exists reports (
  id uuid primary key, work_id uuid not null references works(id), reporter_id uuid not null references users(id),
  reason text not null, status text not null default 'open', created_at timestamptz not null default now(),
  unique(work_id,reporter_id)
);
create table if not exists moderators (user_id uuid primary key references users(id));
create table if not exists moderation_actions (
  id uuid primary key, moderator_id uuid not null references users(id), report_id uuid not null references reports(id),
  decision text not null, reason text not null, created_at timestamptz not null default now()
);
create table if not exists publish_requests (
  user_id uuid not null references users(id), request_id uuid not null,
  fingerprint text not null, work_id uuid not null references works(id), revision_id uuid not null references revisions(id),
  primary key(user_id,request_id)
);
create index if not exists works_feed_idx on works(created_at desc,id desc);
create index if not exists revisions_work_idx on revisions(work_id,created_at desc);

-- New Google accounts receive a gallery profile with the same ID as auth.users.
create or replace function public.create_gallery_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.users(id,email,password_hash,username,display_name)
  values (new.id, new.email, 'oauth', 'user_' || substr(replace(new.id::text,'-',''),1,12),
    left(coalesce(nullif(new.raw_user_meta_data ->> 'full_name',''),nullif(new.raw_user_meta_data ->> 'name',''),'Автор'),80))
  on conflict (id) do update set email = excluded.email;
  return new;
end;
$$;
create trigger create_gallery_user after insert or update of email on auth.users
for each row execute function public.create_gallery_user();

-- Browser clients use the Edge Function. They cannot query these tables directly.
alter table public.users enable row level security;
alter table public.sessions enable row level security;
alter table public.works enable row level security;
alter table public.revisions enable row level security;
alter table public.drafts enable row level security;
alter table public.likes enable row level security;
alter table public.saves enable row level security;
alter table public.follows enable row level security;
alter table public.blocks enable row level security;
alter table public.comments enable row level security;
alter table public.presets enable row level security;
alter table public.reports enable row level security;
alter table public.moderators enable row level security;
alter table public.moderation_actions enable row level security;
alter table public.publish_requests enable row level security;

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;

create index if not exists works_author_idx on public.works(author_id);
create index if not exists comments_work_idx on public.comments(work_id,created_at);
create index if not exists drafts_owner_idx on public.drafts(owner_id,updated_at desc);
create index if not exists reports_status_idx on public.reports(status,created_at desc);

