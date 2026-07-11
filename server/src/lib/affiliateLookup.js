import { config } from '../config.js';
import { buildAffiliateLink } from './affiliate.js';

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null && String(value).length > 0);
}

function cardIdOf(card) {
  const value = Number(firstDefined(card.id, card.card_id, card.cardId));
  return Number.isFinite(value) && value > 0 ? value : null;
}

function namesOf(card) {
  return [...new Set([
    card.name,
    card.card_name,
    card.localized?.name
  ].filter(Boolean).map(name => String(name).trim().toLowerCase()))];
}

function storedLinksByCard(rows, cards) {
  const byId = new Map();
  const byName = new Map();
  for (const row of rows) {
    if (row.card_id != null && !byId.has(Number(row.card_id))) byId.set(Number(row.card_id), row);
    const name = String(row.card_name || '').trim().toLowerCase();
    if (name && !byName.has(name)) byName.set(name, row);
  }
  return new Map(cards.map(card => {
    const id = cardIdOf(card);
    const byExactId = id == null ? null : byId.get(id);
    const byExactName = namesOf(card).map(name => byName.get(name)).find(Boolean);
    return [card, byExactId || byExactName || null];
  }));
}

export function buildCardAffiliate(card, storedAffiliate = null) {
  return buildAffiliateLink(card, {
    links: storedAffiliate
      ? { [firstDefined(card.id, card.card_id, card.cardId, card.name)]: storedAffiliate.affiliate_url }
      : config.AFFILIATE_CARD_LINKS_JSON,
    template: config.AFFILIATE_CARD_URL_TEMPLATE,
    label: storedAffiliate?.label || config.AFFILIATE_LINK_LABEL,
    provider: storedAffiliate?.provider,
    disclosure: config.AFFILIATE_DISCLOSURE
  });
}

export async function findStoredAffiliateLink(client, card) {
  const cardId = cardIdOf(card);
  const names = namesOf(card);
  try {
    const result = await client.query(`
      select card_id, card_name, affiliate_url, provider, label
      from affiliate_links
      where active = true
        and (
          card_id = $1
          or lower(card_name) = any($2::text[])
        )
      order by case when card_id = $1 then 0 else 1 end, updated_at desc
      limit 1
    `, [cardId, names]);
    return result.rows[0] ?? null;
  } catch (error) {
    if (error.code === '42P01') return null;
    throw error;
  }
}

export async function attachAffiliateLinks(client, cards, { onlyMissing = false } = {}) {
  const candidates = cards.filter(card => !onlyMissing || card.status === 'missing');
  if (!candidates.length) return cards;

  const ids = [...new Set(candidates.map(cardIdOf).filter(Boolean))];
  const names = [...new Set(candidates.flatMap(namesOf))];
  let stored = new Map(candidates.map(card => [card, null]));
  try {
    const result = await client.query(`
      select card_id, card_name, affiliate_url, provider, label
      from affiliate_links
      where active = true
        and (
          card_id = any($1::bigint[])
          or lower(card_name) = any($2::text[])
        )
      order by updated_at desc
    `, [ids, names]);
    stored = storedLinksByCard(result.rows, candidates);
  } catch (error) {
    if (error.code !== '42P01') throw error;
  }

  for (const card of candidates) {
    card.affiliate = buildCardAffiliate(card, stored.get(card));
  }
  return cards;
}
