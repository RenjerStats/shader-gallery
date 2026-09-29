create table if not exists dna_jobs (
  id uuid primary key,
  owner_id uuid not null references users(id) on delete cascade,
  request_id uuid not null,
  fingerprint text not null,
  prompt text not null check (length(prompt) between 1 and 2000),
  controls text not null default '' check (length(controls) <= 1000),
  reference_snapshots jsonb not null default '[]',
  created_at timestamptz not null default now(),
  unique(owner_id, request_id)
);
create table if not exists dna_variants (
  id uuid primary key,
  job_id uuid not null references dna_jobs(id) on delete cascade,
  slot smallint not null check (slot in (0,1)),
  model text not null,
  status text not null default 'queued' check (status in ('queued','running','ready','failed','cancelled')),
  attempts smallint not null default 0 check (attempts between 0 and 3),
  lease_token uuid,
  lease_until timestamptz,
  result jsonb,
  error_code text,
  error_message text,
  draft_id uuid references drafts(id) on delete set null,
  updated_at timestamptz not null default now(),
  unique(job_id,slot),
  check ((status = 'ready') = (result is not null)),
  check ((status = 'running') = (lease_token is not null and lease_until is not null))
);
create index if not exists dna_jobs_owner_idx on dna_jobs(owner_id,created_at desc,id desc);
create index if not exists dna_variants_queue_idx on dna_variants(status,updated_at) where status in ('queued','running');
create index if not exists dna_variants_draft_idx on dna_variants(draft_id) where draft_id is not null;
alter table revisions add column if not exists dna_origin jsonb;
