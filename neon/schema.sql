create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id text primary key,
  username text not null check (username ~ '^[a-z0-9_]{3,30}$'),
  created_at timestamptz not null default now()
);

create unique index if not exists profiles_username_lower_key
  on public.profiles (lower(username));

create table if not exists public.legacy_accounts (
  email text primary key check (email = lower(email)),
  legacy_user_id text not null,
  username text not null,
  cards jsonb not null default '[]'::jsonb check (jsonb_typeof(cards) = 'array'),
  created_at timestamptz not null default now(),
  imported_at timestamptz
);

create table if not exists public.cards (
  id uuid primary key default gen_random_uuid(),
  user_id text not null references public.profiles(id) on delete cascade,
  card_id bigint not null check (card_id > 0),
  name text not null check (length(name) between 1 and 200),
  image_url text,
  type text,
  attribute text,
  rarity text,
  collection_name text,
  quantity integer not null default 1 check (quantity between 1 and 999),
  saved_at timestamptz not null default now(),
  unique (user_id, card_id)
);

create index if not exists cards_user_saved_idx on public.cards(user_id, saved_at desc);
create index if not exists cards_name_idx on public.cards using gin (to_tsvector('simple', name));

create or replace view public.cards_public as
select p.username, c.card_id, c.name, c.image_url, c.type, c.attribute,
       c.rarity, c.quantity, c.saved_at, c.collection_name
from public.cards c
join public.profiles p on p.id = c.user_id;
