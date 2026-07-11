import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth.js';
import { pool, transaction } from '../db.js';
import { sseHub } from '../lib/sseHub.js';
import { attachAffiliateLinks } from '../lib/affiliateLookup.js';

export const decksRouter = Router();

const deckCardSchema = z.object({
  cardId: z.coerce.number().int().positive(),
  name: z.string().trim().min(1).max(200),
  imageUrl: z.string().url().optional().nullable(),
  type: z.string().max(100).optional().nullable(),
  attribute: z.string().max(30).optional().nullable(),
  rarity: z.string().max(100).optional().nullable(),
  status: z.enum(['owned', 'missing']),
  quantity: z.coerce.number().int().min(1).max(999).default(1)
});

export const deckSchema = z.object({
  id: z.string().uuid().optional(),
  name: z.string().trim().min(1).max(80),
  cards: z.array(deckCardSchema).max(500).default([])
});

export function mapDecks(rows) {
  const decks = new Map();
  for (const row of rows) {
    if (!decks.has(row.deck_id)) {
      decks.set(row.deck_id, {
        id: row.deck_id,
        name: row.deck_name,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        cards: []
      });
    }
    if (row.card_id != null) {
      decks.get(row.deck_id).cards.push({
        cardId: Number(row.card_id), name: row.card_name, imageUrl: row.image_url,
        type: row.type, attribute: row.attribute, rarity: row.rarity,
        status: row.status, quantity: row.quantity
      });
    }
  }
  return [...decks.values()];
}

async function readDecks(userId, client = pool) {
  const result = await client.query(`
    select d.id as deck_id, d.name as deck_name, d.created_at, d.updated_at,
           dc.card_id, dc.name as card_name, dc.image_url, dc.type, dc.attribute,
           dc.rarity, dc.status, dc.quantity
    from decks d left join deck_cards dc on dc.deck_id = d.id
    where d.user_id = $1
    order by d.updated_at desc, dc.added_at, dc.name
  `, [userId]);
  const decks = mapDecks(result.rows);
  await attachAffiliateLinks(client, decks.flatMap(deck => deck.cards), { onlyMissing: true });
  return decks;
}

async function replaceDeckCards(client, deckId, cards) {
  await client.query('delete from deck_cards where deck_id = $1', [deckId]);
  const deduplicated = [...new Map(cards.map(card => [card.cardId, card])).values()];
  if (!deduplicated.length) return;
  await client.query(`
    insert into deck_cards(deck_id, card_id, name, image_url, type, attribute, rarity, status, quantity)
    select $1, x.card_id, x.name, x.image_url, x.type, x.attribute, x.rarity, x.status, x.quantity
    from jsonb_to_recordset($2::jsonb) as x(
      card_id bigint, name text, image_url text, type text, attribute text,
      rarity text, status text, quantity integer
    )
  `, [deckId, JSON.stringify(deduplicated.map(card => ({
    card_id: card.cardId, name: card.name, image_url: card.imageUrl,
    type: card.type, attribute: card.attribute, rarity: card.rarity,
    status: card.status, quantity: card.quantity
  })))]);
}

async function notifyPublicProfile(userId) {
  const profile = await pool.query('select username from profiles where id = $1', [userId]);
  const username = profile.rows[0]?.username;
  if (username) sseHub.publish(username);
}

decksRouter.use(requireAuth);

decksRouter.get('/', async (req, res) => {
  res.json({ decks: await readDecks(req.auth.user.id) });
});

decksRouter.post('/', async (req, res) => {
  const deck = deckSchema.parse(req.body);
  const id = deck.id ?? randomUUID();
  await transaction(async client => {
    await client.query(`
      insert into decks(id, user_id, name) values ($1, $2, $3)
      on conflict (id) do update set name = excluded.name, updated_at = now()
      where decks.user_id = excluded.user_id
    `, [id, req.auth.user.id, deck.name]);
    const owned = await client.query('select 1 from decks where id = $1 and user_id = $2', [id, req.auth.user.id]);
    if (!owned.rowCount) throw Object.assign(new Error('Deck nao encontrado'), { status: 404 });
    await replaceDeckCards(client, id, deck.cards);
  });
  const decks = await readDecks(req.auth.user.id);
  await notifyPublicProfile(req.auth.user.id);
  res.status(201).json(decks.find(item => item.id === id));
});

decksRouter.put('/:id', async (req, res) => {
  const id = z.string().uuid().parse(req.params.id);
  const deck = deckSchema.omit({ id: true }).parse(req.body);
  await transaction(async client => {
    const result = await client.query(
      'update decks set name = $1, updated_at = now() where id = $2 and user_id = $3',
      [deck.name, id, req.auth.user.id]
    );
    if (!result.rowCount) throw Object.assign(new Error('Deck nao encontrado'), { status: 404 });
    await replaceDeckCards(client, id, deck.cards);
  });
  const decks = await readDecks(req.auth.user.id);
  await notifyPublicProfile(req.auth.user.id);
  res.json(decks.find(item => item.id === id));
});

decksRouter.delete('/:id', async (req, res) => {
  const id = z.string().uuid().parse(req.params.id);
  const result = await pool.query('delete from decks where id = $1 and user_id = $2', [id, req.auth.user.id]);
  if (!result.rowCount) return res.status(404).json({ error: 'Deck nao encontrado' });
  await notifyPublicProfile(req.auth.user.id);
  res.status(204).end();
});

decksRouter.post('/sync', async (req, res) => {
  const decks = z.array(deckSchema.extend({ id: z.string().uuid() })).max(200).parse(req.body.decks ?? req.body);
  const userId = req.auth.user.id;
  const ids = decks.map(deck => deck.id);
  await transaction(async client => {
    await client.query('delete from decks where user_id = $1 and not (id = any($2::uuid[]))', [userId, ids]);
    for (const deck of decks) {
      await client.query(`
        insert into decks(id, user_id, name) values ($1, $2, $3)
        on conflict (id) do update set name = excluded.name, updated_at = now()
        where decks.user_id = excluded.user_id
      `, [deck.id, userId, deck.name]);
      const owned = await client.query('select 1 from decks where id = $1 and user_id = $2', [deck.id, userId]);
      if (!owned.rowCount) {
        throw Object.assign(new Error('Identificador de deck indisponivel'), { status: 409 });
      }
      await replaceDeckCards(client, deck.id, deck.cards);
    }
  });
  await notifyPublicProfile(userId);
  res.json({ synced: decks.length });
});
