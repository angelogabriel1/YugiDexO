import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth.js';
import { pool, transaction } from '../db.js';
import { sseHub } from '../lib/sseHub.js';
import { cardImageUrl } from '../lib/cardImages.js';

export const cardsRouter = Router();
const cardSchema = z.object({
  cardId: z.coerce.number().int().positive(),
  name: z.string().trim().min(1).max(200),
  imageUrl: z.string().url().optional().nullable(),
  type: z.string().max(100).optional().nullable(),
  attribute: z.string().max(30).optional().nullable(),
  rarity: z.string().max(100).optional().nullable(),
  collectionName: z.string().trim().max(200).optional().nullable(),
  quantity: z.coerce.number().int().min(1).max(999).default(1),
  savedAt: z.string().datetime().optional()
});

cardsRouter.get('/', requireAuth, async (req, res) => {
  const result = await pool.query(`
    select card_id, name, image_url, type, attribute, rarity, collection_name, quantity, saved_at
    from cards where user_id = $1 order by saved_at desc
  `, [req.auth.user.id]);
  res.json({
    cards: result.rows.map(card => ({
      cardId: Number(card.card_id),
      name: card.name,
      imageUrl: cardImageUrl(card.card_id, card.image_url),
      type: card.type,
      attribute: card.attribute,
      rarity: card.rarity,
      collectionName: card.collection_name,
      quantity: card.quantity,
      savedAt: card.saved_at
    }))
  });
});

cardsRouter.post('/sync', requireAuth, async (req, res) => {
  const cards = z.array(cardSchema).max(5000).parse(req.body.cards ?? req.body);
  const deduplicated = [...new Map(cards.map(card => [card.cardId, card])).values()];
  const userId = req.auth.user.id;
  await transaction(async client => {
    await client.query('delete from cards where user_id = $1', [userId]);
    if (!deduplicated.length) return;
    await client.query(`
      insert into cards(user_id, card_id, name, image_url, type, attribute, rarity, collection_name, quantity, saved_at)
      select $1, x.card_id, x.name, x.image_url, x.type, x.attribute, x.rarity,
             x.collection_name, greatest(1, least(999, coalesce(x.quantity, 1))), coalesce(x.saved_at, now())
      from jsonb_to_recordset($2::jsonb) as x(
        card_id bigint, name text, image_url text, type text, attribute text,
        rarity text, collection_name text, quantity integer, saved_at timestamptz
      )
    `, [userId, JSON.stringify(deduplicated.map(card => ({
      card_id: card.cardId, name: card.name, image_url: card.imageUrl,
      type: card.type, attribute: card.attribute, rarity: card.rarity,
      collection_name: card.collectionName, quantity: card.quantity,
      saved_at: card.savedAt ?? new Date().toISOString()
    })))]);
  });

  const profile = await pool.query('select username from profiles where id = $1', [userId]);
  const username = profile.rows[0]?.username;
  if (!username) {
    return res.status(409).json({ error: 'Perfil nao encontrado; entre novamente na sua conta' });
  }
  sseHub.publish(username);
  res.json({ synced: deduplicated.length, username, publicUrl: `/colecao/${username}` });
});
