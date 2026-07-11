const defaultDisclosure = 'Link de afiliado: podemos receber comissão sem custo extra para você.';

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null && String(value).length > 0);
}

function normalizeKey(value) {
  return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
}

function parseLinks(value) {
  if (!value) return {};
  if (typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string') return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function affiliateResponse(url, options) {
  try {
    return {
      url: new URL(url).toString(),
      label: options.label?.trim() || 'Ver oferta da carta',
      provider: options.provider?.trim() || null,
      disclosure: options.disclosure?.trim() || defaultDisclosure
    };
  } catch {
    return null;
  }
}

function mappedAffiliateUrl(card, links) {
  const cardId = String(firstDefined(card.id, card.card_id, card.cardId, '')).trim();
  const name = String(firstDefined(card.localized?.name, card.name, '')).trim();
  const candidates = [cardId, name].filter(Boolean);

  for (const key of candidates) {
    if (links[key]) return links[key];
  }

  const normalized = new Map(Object.entries(links).map(([key, url]) => [normalizeKey(key), url]));
  for (const key of candidates) {
    const url = normalized.get(normalizeKey(key));
    if (url) return url;
  }
  return null;
}

export function buildAffiliateLink(card, options = {}) {
  const mappedUrl = mappedAffiliateUrl(card, parseLinks(options.links));
  if (mappedUrl) return affiliateResponse(mappedUrl, options);

  const template = options.template?.trim();
  if (!template) return null;

  const cardId = String(firstDefined(card.id, card.card_id, card.cardId, '')).trim();
  const name = String(firstDefined(card.localized?.name, card.name, '')).trim();
  const encodedCardId = encodeURIComponent(cardId);
  const encodedName = encodeURIComponent(name);
  const url = template.replace(/\{(id|cardId|card_id|name|encodedName|query)\}/g, (_, key) => {
    if (key === 'id' || key === 'cardId' || key === 'card_id') return encodedCardId;
    return encodedName;
  });

  return affiliateResponse(url, options);
}
