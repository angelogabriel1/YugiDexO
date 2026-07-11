import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth.js';
import { requireAdmin } from '../middleware/admin.js';
import { pool } from '../db.js';
import { getCardDetails } from '../services/cardService.js';
import { cardImageUrl } from '../lib/cardImages.js';

export const adminRouter = Router();

const nullableText = max => z.preprocess(value => {
  if (typeof value !== 'string') return value ?? null;
  const trimmed = value.trim();
  return trimmed.length ? trimmed : null;
}, z.string().max(max).nullable());

const cardIdField = z.preprocess(value => {
  if (value === '' || value === undefined || value === null) return null;
  return value;
}, z.coerce.number().int().positive().nullable());

export const affiliateLinkSchema = z.object({
  cardId: cardIdField.default(null),
  cardName: z.string().trim().min(1).max(200),
  affiliateUrl: z.string().trim().url().max(600),
  provider: z.string().trim().min(1).max(80).default('Mercado Livre'),
  label: nullableText(120).default(null),
  active: z.boolean().default(true),
  notes: nullableText(500).default(null)
});

function mapAffiliate(row) {
  return {
    id: row.id,
    cardId: row.card_id == null ? null : Number(row.card_id),
    cardName: row.card_name,
    affiliateUrl: row.affiliate_url,
    provider: row.provider,
    label: row.label,
    active: row.active,
    notes: row.notes,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

async function listAffiliateLinks(query = '') {
  const q = query.trim();
  const result = await pool.query(`
    select id, card_id, card_name, affiliate_url, provider, label, active, notes, created_at, updated_at
    from affiliate_links
    where $1 = ''
       or lower(card_name) like '%' || lower($1) || '%'
       or provider ilike '%' || $1 || '%'
       or card_id::text like '%' || $1 || '%'
    order by updated_at desc, card_name
    limit 250
  `, [q]);
  return result.rows.map(mapAffiliate);
}

adminRouter.use(requireAuth, requireAdmin);

adminRouter.get('/me', (req, res) => {
  res.json({
    admin: true,
    user: {
      id: req.auth.user.id,
      name: req.auth.user.name,
      email: req.auth.user.email
    }
  });
});

adminRouter.get('/affiliate-links', async (req, res) => {
  const query = z.string().max(120).optional().default('').parse(req.query.query);
  res.json({ links: await listAffiliateLinks(query) });
});

adminRouter.post('/affiliate-links', async (req, res) => {
  const input = affiliateLinkSchema.parse(req.body);
  try {
    const result = await pool.query(`
      insert into affiliate_links(card_id, card_name, affiliate_url, provider, label, active, notes)
      values ($1, $2, $3, $4, $5, $6, $7)
      returning id, card_id, card_name, affiliate_url, provider, label, active, notes, created_at, updated_at
    `, [input.cardId, input.cardName, input.affiliateUrl, input.provider, input.label, input.active, input.notes]);
    res.status(201).json(mapAffiliate(result.rows[0]));
  } catch (error) {
    if (error.code === '23505') return res.status(409).json({ error: 'Esta carta ja possui um link afiliado cadastrado.' });
    throw error;
  }
});

adminRouter.put('/affiliate-links/:id', async (req, res) => {
  const id = z.string().uuid().parse(req.params.id);
  const input = affiliateLinkSchema.parse(req.body);
  try {
    const result = await pool.query(`
      update affiliate_links
      set card_id = $1, card_name = $2, affiliate_url = $3, provider = $4,
          label = $5, active = $6, notes = $7, updated_at = now()
      where id = $8
      returning id, card_id, card_name, affiliate_url, provider, label, active, notes, created_at, updated_at
    `, [input.cardId, input.cardName, input.affiliateUrl, input.provider, input.label, input.active, input.notes, id]);
    if (!result.rowCount) return res.status(404).json({ error: 'Link afiliado nao encontrado' });
    res.json(mapAffiliate(result.rows[0]));
  } catch (error) {
    if (error.code === '23505') return res.status(409).json({ error: 'Esta carta ja possui outro link afiliado cadastrado.' });
    throw error;
  }
});

adminRouter.delete('/affiliate-links/:id', async (req, res) => {
  const id = z.string().uuid().parse(req.params.id);
  const result = await pool.query('delete from affiliate_links where id = $1', [id]);
  if (!result.rowCount) return res.status(404).json({ error: 'Link afiliado nao encontrado' });
  res.status(204).end();
});

adminRouter.get('/card-lookup', async (req, res) => {
  const query = z.object({
    id: z.coerce.number().int().positive().optional(),
    name: z.string().trim().min(1).max(200).optional()
  }).refine(value => value.id || value.name, 'Informe id ou name').parse(req.query);
  const card = await getCardDetails(query);
  const existing = await listAffiliateLinks(String(card.id));
  res.json({
    card: {
      cardId: Number(card.id),
      name: card.localized?.name || card.name,
      originalName: card.name,
      imageUrl: cardImageUrl(card.id, card.card_images?.[0]?.image_url)
    },
    existing: existing.find(link => link.cardId === Number(card.id)) ?? null
  });
});
