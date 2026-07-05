import { Router } from 'express';
import { z } from 'zod';
import { pool, transaction } from '../db.js';
import { neonAuthRequest } from '../lib/neonAuth.js';
import { requireAuth } from '../middleware/auth.js';

export const authRouter = Router();
const credentials = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(128),
  username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/).optional()
});

function normalizeUsername(value) {
  return value.toLowerCase().replace(/[^a-z0-9_]/g, '_').replace(/_+/g, '_').slice(0, 30);
}

async function prepareAccount(user, preferred, email) {
  return transaction(async client => {
    let profile = (await client.query('select username from profiles where id = $1', [user.id])).rows[0];
    if (!profile) {
      let username = normalizeUsername(preferred || user.name || user.email?.split('@')[0] || `duelista_${user.id.slice(-6)}`);
      if (username.length < 3) username = `duelista_${user.id.slice(-6)}`;
      const collision = await client.query('select 1 from profiles where lower(username) = lower($1)', [username]);
      if (collision.rowCount) username = `${username.slice(0, 22)}_${user.id.slice(-6).toLowerCase()}`;
      profile = (await client.query(
        'insert into profiles(id, username) values ($1, $2) returning username',
        [user.id, username]
      )).rows[0];
    }

    const normalizedEmail = (email || user.email || '').trim().toLowerCase();
    const legacy = normalizedEmail
      ? (await client.query(
          'select cards from legacy_accounts where email = $1 and imported_at is null for update',
          [normalizedEmail]
        )).rows[0]
      : null;
    const legacyCards = Array.isArray(legacy?.cards) ? legacy.cards : [];
    if (legacy) {
      if (legacyCards.length) {
        await client.query(`
          insert into cards(user_id, card_id, name, image_url, type, attribute, rarity, collection_name, quantity, saved_at)
          select $1, x.card_id, x.name, x.image_url, x.type, x.attribute, x.rarity,
                 x.collection_name, greatest(1, least(999, coalesce(x.quantity, 1))), coalesce(x.saved_at, now())
          from jsonb_to_recordset($2::jsonb) as x(
            card_id bigint, name text, image_url text, type text, attribute text,
            rarity text, collection_name text, quantity integer, saved_at timestamptz
          )
          on conflict (user_id, card_id) do update set
            name = excluded.name, image_url = excluded.image_url, type = excluded.type,
            attribute = excluded.attribute, rarity = excluded.rarity,
            collection_name = excluded.collection_name, quantity = excluded.quantity,
            saved_at = excluded.saved_at
        `, [user.id, JSON.stringify(legacyCards)]);
      }
      await client.query('update legacy_accounts set imported_at = now() where email = $1', [normalizedEmail]);
    }
    return { ...profile, restoredCards: legacyCards.length };
  });
}

authRouter.post('/register', async (req, res) => {
  const input = credentials.extend({ username: credentials.shape.username.unwrap() }).parse(req.body);
  const normalized = normalizeUsername(input.username);
  const normalizedEmail = input.email.trim().toLowerCase();
  const legacy = await pool.query(
    'select username from legacy_accounts where email = $1 and imported_at is null',
    [normalizedEmail]
  );
  const desiredUsername = legacy.rows[0]?.username ?? normalized;
  const reserved = await pool.query(`
    select 1 from legacy_accounts
    where lower(username) = lower($1) and email <> $2 and imported_at is null
  `, [desiredUsername, normalizedEmail]);
  if (reserved.rowCount) return res.status(409).json({ error: 'Username reservado para uma conta legada' });
  const { rowCount } = await pool.query('select 1 from profiles where lower(username) = lower($1)', [desiredUsername]);
  if (rowCount) return res.status(409).json({ error: 'Username ja esta em uso' });

  const auth = await neonAuthRequest('/sign-up/email', {
    body: { name: desiredUsername, email: normalizedEmail, password: input.password }
  });
  const user = auth.data?.user ?? auth.data?.data?.user;
  if (!user?.id) throw Object.assign(new Error('Neon Auth nao retornou o usuario criado'), { status: 502 });
  const profile = await prepareAccount(user, desiredUsername, normalizedEmail);
  res.status(201).json({
    token: auth.token,
    username: profile.username,
    restoredCards: profile.restoredCards,
    requiresEmailConfirmation: !auth.token
  });
});

authRouter.post('/login', async (req, res) => {
  const input = credentials.omit({ username: true }).parse(req.body);
  const auth = await neonAuthRequest('/sign-in/email', { body: input });
  const user = auth.data?.user ?? auth.data?.data?.user;
  if (!user?.id || !auth.token) return res.status(401).json({ error: 'Email ou senha invalidos' });
  const profile = await prepareAccount(user, undefined, input.email);
  res.json({ token: auth.token, refreshToken: null, username: profile.username, restoredCards: profile.restoredCards });
});

authRouter.post('/logout', requireAuth, async (req, res) => {
  await pool.query('delete from neon_auth.session where id = $1', [req.auth.session.id]);
  res.status(204).end();
});
