import test from 'node:test';
import assert from 'node:assert/strict';
import { fallbackPrices } from '../src/lib/cardPrices.js';

test('usa a menor cotacao positiva por edicao e converte para reais', () => {
  const prices = fallbackPrices({
    card_sets: [
      { set_name: 'Edicao cara', set_code: 'ABC-001', set_rarity: 'Ultra Rare', set_price: '10.00' },
      { set_name: 'Sem cotacao', set_code: 'ABC-002', set_price: '0.00' },
      { set_name: 'Edicao barata', set_code: 'ABC-003', set_rarity: 'Common', set_price: '2.50' }
    ],
    card_prices: [{ cardmarket_price: '0.01' }]
  }, 5);

  assert.equal(prices.source, 'ygoprodeck-editions');
  assert.equal(prices.min, 12.5);
  assert.equal(prices.max, 50);
  assert.match(prices.editions[0].edition, /Edicao barata/);
});

test('usa mercados somente quando nao ha cotacao por edicao', () => {
  const prices = fallbackPrices({
    card_sets: [{ set_name: 'Indisponivel', set_price: '0.00' }],
    card_prices: [{ cardmarket_price: '3', tcgplayer_price: '1.50', ebay_price: '0' }]
  }, 4);

  assert.equal(prices.source, 'ygoprodeck-markets');
  assert.equal(prices.min, 6);
  assert.deepEqual(prices.editions.map(item => item.edition), ['TCGplayer', 'Cardmarket']);
});

test('nao transforma ausencia de cotacao em preco zero', () => {
  assert.equal(fallbackPrices({ card_sets: [], card_prices: [{}] }, 5), null);
});
