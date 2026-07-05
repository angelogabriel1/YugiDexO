-- Execute no SQL Editor do Supabase. A funcao replace_inventory faz a troca
-- atomica: a colecao antiga nunca fica parcialmente substituida.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,30}$'),
  created_at timestamptz not null default now()
);

create table if not exists public.cards (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  card_id bigint not null check (card_id > 0),
  name text not null check (length(name) between 1 and 200),
  image_url text,
  type text,
  attribute text,
  rarity text,
  collection_name text,
  quantity integer not null default 1 check (quantity between 1 and 999),
  estimated_unit_value numeric(12, 2) check (estimated_unit_value is null or estimated_unit_value >= 0),
  saved_at timestamptz not null default now(),
  unique (user_id, card_id)
);

alter table public.cards add column if not exists collection_name text;
alter table public.cards add column if not exists estimated_unit_value numeric(12, 2)
  check (estimated_unit_value is null or estimated_unit_value >= 0);

create index if not exists cards_user_saved_idx on public.cards(user_id, saved_at desc);
create index if not exists cards_name_idx on public.cards using gin (to_tsvector('simple', name));

alter table public.profiles enable row level security;
alter table public.cards enable row level security;

drop policy if exists "profile readable by owner" on public.profiles;
create policy "profile readable by owner" on public.profiles for select using (auth.uid() = id);

drop policy if exists "cards readable by owner" on public.cards;
create policy "cards readable by owner" on public.cards for select using (auth.uid() = user_id);
drop policy if exists "cards inserted by owner" on public.cards;
create policy "cards inserted by owner" on public.cards for insert with check (auth.uid() = user_id);
drop policy if exists "cards updated by owner" on public.cards;
create policy "cards updated by owner" on public.cards for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "cards deleted by owner" on public.cards;
create policy "cards deleted by owner" on public.cards for delete using (auth.uid() = user_id);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare requested_username text;
begin
  requested_username := lower(coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1)));
  if requested_username !~ '^[a-z0-9_]{3,30}$' then
    raise exception 'Username invalido';
  end if;
  insert into public.profiles(id, username) values (new.id, requested_username);
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();

create or replace function public.replace_inventory(new_cards jsonb)
returns integer language plpgsql security invoker set search_path = public as $$
declare inserted_count integer;
begin
  if auth.uid() is null then raise exception 'Nao autenticado'; end if;
  if jsonb_typeof(new_cards) <> 'array' then raise exception 'Inventario deve ser um array'; end if;

  delete from public.cards where user_id = auth.uid();
  insert into public.cards(user_id, card_id, name, image_url, type, attribute, rarity, collection_name, quantity, estimated_unit_value, saved_at)
  select auth.uid(), x.card_id, x.name, x.image_url, x.type, x.attribute, x.rarity, x.collection_name,
         greatest(1, least(999, coalesce(x.quantity, 1))), x.estimated_unit_value, coalesce(x.saved_at, now())
  from jsonb_to_recordset(new_cards) as x(
    user_id uuid, card_id bigint, name text, image_url text, type text,
    attribute text, rarity text, collection_name text, quantity integer, estimated_unit_value numeric, saved_at timestamptz
  )
  where x.card_id > 0 and length(x.name) between 1 and 200
  on conflict (user_id, card_id) do update set
    name = excluded.name, image_url = excluded.image_url, type = excluded.type,
    attribute = excluded.attribute, rarity = excluded.rarity, collection_name = excluded.collection_name,
    quantity = excluded.quantity, estimated_unit_value = excluded.estimated_unit_value, saved_at = excluded.saved_at;
  get diagnostics inserted_count = row_count;
  return inserted_count;
end;
$$;

revoke all on function public.replace_inventory(jsonb) from public;
grant execute on function public.replace_inventory(jsonb) to authenticated;
grant select on public.profiles to authenticated;
grant select, insert, update, delete on public.cards to authenticated;

create or replace view public.cards_public with (security_invoker = false) as
select p.username, c.card_id, c.name, c.image_url, c.type, c.attribute,
       c.rarity, c.quantity, c.saved_at, c.collection_name, c.estimated_unit_value
from public.cards c join public.profiles p on p.id = c.user_id;

revoke all on public.cards_public from anon, authenticated;
