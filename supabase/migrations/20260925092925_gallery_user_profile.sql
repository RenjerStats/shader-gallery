create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.create_gallery_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.users(id,email,password_hash,username,display_name)
  values (
    new.id,
    new.email,
    'oauth',
    'user_' || substr(replace(new.id::text,'-',''),1,12),
    left(coalesce(
      nullif(new.raw_user_meta_data ->> 'display_name',''),
      nullif(new.raw_user_meta_data ->> 'full_name',''),
      nullif(new.raw_user_meta_data ->> 'name',''),
      'Автор'
    ),80)
  )
  on conflict (id) do update set email = excluded.email;
  return new;
end;
$$;
revoke all on function private.create_gallery_user() from public, anon, authenticated;

drop trigger if exists create_gallery_user on auth.users;
create trigger create_gallery_user after insert or update of email on auth.users
for each row execute function private.create_gallery_user();
drop function if exists public.create_gallery_user();
