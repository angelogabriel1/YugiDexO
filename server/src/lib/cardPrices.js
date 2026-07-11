function positiveNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function inBrl(usd, rate) {
  return Number((usd * rate).toFixed(2));
}

export function fallbackPrices(card, brlUsdRate) {
  const sets = (card.card_sets ?? []).flatMap(set => {
    const usd = positiveNumber(set.set_price);
    if (usd == null) return [];

    const details = [set.set_code, set.set_rarity].filter(Boolean).join(' • ');
    return [{
      edition: [set.set_name, details].filter(Boolean).join(' — '),
      price: inBrl(usd, brlUsdRate)
    }];
  });

  // card_prices is an aggregate by marketplace. It is less precise than the
  // edition prices above, but remains useful for cards without card_sets.
  const raw = card.card_prices?.[0] ?? {};
  const markets = [
    ['Cardmarket', raw.cardmarket_price],
    ['TCGplayer', raw.tcgplayer_price],
    ['eBay', raw.ebay_price],
    ['CoolStuffInc', raw.coolstuffinc_price],
    ['Amazon', raw.amazon_price]
  ].flatMap(([market, value]) => {
    const usd = positiveNumber(value);
    return usd == null ? [] : [{ edition: market, price: inBrl(usd, brlUsdRate) }];
  });

  const editions = (sets.length ? sets : markets).sort((a, b) => a.price - b.price);
  if (!editions.length) return null;

  return {
    source: sets.length ? 'ygoprodeck-editions' : 'ygoprodeck-markets',
    currency: 'BRL',
    min: editions[0].price,
    max: editions.at(-1).price,
    usdRate: brlUsdRate,
    editions
  };
}
