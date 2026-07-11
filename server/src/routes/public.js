import { Router } from 'express';
import { z } from 'zod';
import { pool } from '../db.js';
import { sseHub } from '../lib/sseHub.js';
import { getCardDetails } from '../services/cardService.js';
import { cardImageUrl } from '../lib/cardImages.js';
import { buildAffiliateLink } from '../lib/affiliate.js';
import { config } from '../config.js';
import { mapDecks } from './decks.js';

export const publicRouter = Router();
const username = z.string().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/);

function normalizedNames(card) {
  return [...new Set([card.name, card.localized?.name].filter(Boolean).map(name => String(name).trim().toLowerCase()))];
}

async function findAffiliateLink(card) {
  const cardId = Number(card.id);
  const names = normalizedNames(card);
  try {
    const result = await pool.query(`
      select card_id, card_name, affiliate_url, provider, label
      from affiliate_links
      where active = true
        and (
          card_id = $1
          or lower(card_name) = any($2::text[])
        )
      order by case when card_id = $1 then 0 else 1 end, updated_at desc
      limit 1
    `, [Number.isFinite(cardId) ? cardId : null, names]);
    return result.rows[0] ?? null;
  } catch (error) {
    if (error.code === '42P01') return null;
    throw error;
  }
}

publicRouter.get('/profiles/:username', async (req, res) => {
  const handle = username.parse(req.params.username).toLowerCase();
  const profile = (await pool.query(
    'select username, created_at from profiles where lower(username) = lower($1)',
    [handle]
  )).rows[0];
  if (!profile) return res.status(404).json({ error: 'Duelista nao encontrado' });
  res.set('Cache-Control', 'no-store');
  res.json({ username: profile.username, createdAt: profile.created_at, publicUrl: `/colecao/${profile.username}` });
});

publicRouter.get('/collections/:username', async (req, res) => {
  const handle = username.parse(req.params.username).toLowerCase();
  const profileResult = await pool.query('select id, username, created_at from profiles where lower(username) = lower($1)', [handle]);
  const profile = profileResult.rows[0];
  if (!profile) return res.status(404).json({ error: 'Duelista nao encontrado' });
  const [cards, deckRows] = await Promise.all([
    pool.query(`
      select username, card_id, name, image_url, type, attribute, rarity, quantity, saved_at,
             collection_name, estimated_unit_value
      from cards_public where lower(username) = lower($1) order by saved_at desc
    `, [handle]),
    pool.query(`
      select d.id as deck_id, d.name as deck_name, d.created_at, d.updated_at,
             dc.card_id, dc.name as card_name, dc.image_url, dc.type, dc.attribute,
             dc.rarity, dc.status, dc.quantity, dc.added_at
      from decks d left join deck_cards dc on dc.deck_id = d.id
      where d.user_id = $1
      order by d.updated_at desc, dc.status, dc.added_at, dc.name
    `, [profile.id])
  ]);
  const decks = mapDecks(deckRows.rows).map(deck => ({
    ...deck,
    cards: deck.cards.map(card => ({
      ...card,
      imageUrl: cardImageUrl(card.cardId, card.imageUrl)
    }))
  }));
  res.set('Cache-Control', 'public, max-age=15, stale-while-revalidate=60');
  res.json({
    profile: { username: profile.username, createdAt: profile.created_at },
    cards: cards.rows.map(card => ({
      ...card,
      estimated_unit_value: card.estimated_unit_value == null ? null : Number(card.estimated_unit_value),
      image_url: cardImageUrl(card.card_id, card.image_url)
    })),
    decks
  });
});

publicRouter.get('/card-details', async (req, res) => {
  const query = z.object({ id: z.coerce.number().int().positive().optional(), name: z.string().min(1).max(200).optional() })
    .refine(value => value.id || value.name, 'Informe id ou name').parse(req.query);
  const card = await getCardDetails(query);
  const storedAffiliate = await findAffiliateLink(card);
  res.json({
    ...card,
    affiliate: buildAffiliateLink(card, {
      links: storedAffiliate
        ? { [card.id]: storedAffiliate.affiliate_url }
        : config.AFFILIATE_CARD_LINKS_JSON,
      template: config.AFFILIATE_CARD_URL_TEMPLATE,
      label: storedAffiliate?.label || config.AFFILIATE_LINK_LABEL,
      provider: storedAffiliate?.provider,
      disclosure: config.AFFILIATE_DISCLOSURE
    })
  });
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
