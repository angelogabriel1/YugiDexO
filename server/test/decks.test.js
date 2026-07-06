import test from 'node:test';
import assert from 'node:assert/strict';
import { deckSchema, mapDecks } from '../src/routes/decks.js';

test('valida cartas do deck como owned ou missing', () => {
  const base = { cardId: 46986414, name: 'Dark Magician', quantity: 1 };
  const deck = deckSchema.parse({
    name: 'Meu Deck Principal',
    cards: [{ ...base, status: 'owned' }, { ...base, cardId: 89631139, status: 'missing' }]
  });

  assert.deepEqual(deck.cards.map(card => card.status), ['owned', 'missing']);
  assert.throws(() => deckSchema.parse({ name: 'Inválido', cards: [{ ...base, status: 'unknown' }] }));
});

test('agrupa linhas do banco em decks com suas cartas', () => {
  const rows = [
    {
      deck_id: '11111111-1111-4111-8111-111111111111', deck_name: 'Torneio Inicial',
      created_at: '2026-07-05T00:00:00Z', updated_at: '2026-07-05T00:00:00Z',
      card_id: '46986414', card_name: 'Dark Magician', image_url: null,
      type: 'Normal Monster', attribute: 'DARK', rarity: null, status: 'owned', quantity: 1
    },
    {
      deck_id: '11111111-1111-4111-8111-111111111111', deck_name: 'Torneio Inicial',
      created_at: '2026-07-05T00:00:00Z', updated_at: '2026-07-05T00:00:00Z',
      card_id: '89631139', card_name: 'Blue-Eyes White Dragon', image_url: null,
      type: 'Normal Monster', attribute: 'LIGHT', rarity: null, status: 'missing', quantity: 1
    }
  ];

  const [deck] = mapDecks(rows);
  assert.equal(deck.name, 'Torneio Inicial');
  assert.equal(deck.cards.length, 2);
  assert.equal(deck.cards[1].status, 'missing');
});
