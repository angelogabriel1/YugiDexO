import { Router } from 'express';
import { z } from 'zod';
import { pool } from '../db.js';
import { sseHub } from '../lib/sseHub.js';
import { getCardDetails } from '../services/cardService.js';
import { cardImageUrl } from '../lib/cardImages.js';

export const publicRouter = Router();
const username = z.string().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/);

publicRouter.get('/collections/:username', async (req, res) => {
  const handle = username.parse(req.params.username).toLowerCase();
  const profileResult = await pool.query('select id, username, created_at from profiles where lower(username) = lower($1)', [handle]);
  const profile = profileResult.rows[0];
  if (!profile) return res.status(404).json({ error: 'Duelista nao encontrado' });
  const cards = await pool.query(`
    select username, card_id, name, image_url, type, attribute, rarity, quantity, saved_at,
           collection_name, estimated_unit_value
    from cards_public where lower(username) = lower($1) order by saved_at desc
  `, [handle]);
  res.set('Cache-Control', 'public, max-age=15, stale-while-revalidate=60');
  res.json({
    profile: { username: profile.username, createdAt: profile.created_at },
    cards: cards.rows.map(card => ({
      ...card,
      estimated_unit_value: card.estimated_unit_value == null ? null : Number(card.estimated_unit_value),
      image_url: cardImageUrl(card.card_id, card.image_url)
    }))
  });
});

publicRouter.get('/card-details', async (req, res) => {
  const query = z.object({ id: z.coerce.number().int().positive().optional(), name: z.string().min(1).max(200).optional() })
    .refine(value => value.id || value.name, 'Informe id ou name').parse(req.query);
  res.json(await getCardDetails(query));
});

publicRouter.get('/colecao-stream/:username', (req, res) => {
  const handle = username.parse(req.params.username).toLowerCase();
  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no'
  });
  res.flushHeaders();
  const unsubscribe = sseHub.subscribe(handle, res);
  const heartbeat = setInterval(() => res.write(': heartbeat\n\n'), 25_000);
  req.on('close', () => { clearInterval(heartbeat); unsubscribe(); });
});
