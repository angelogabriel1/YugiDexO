const defaultDisclosure = 'Link de afiliado: podemos receber comissão sem custo extra para você.';

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null && String(value).length > 0);
}

export function buildAffiliateLink(card, options = {}) {
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

  try {
    return {
      url: new URL(url).toString(),
      label: options.label?.trim() || 'Ver oferta da carta',
      disclosure: options.disclosure?.trim() || defaultDisclosure
    };
  } catch {
    return null;
  }
}
