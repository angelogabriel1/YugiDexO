import { pool } from '../src/db.js';
import { getCardDetails } from '../src/services/cardService.js';

const refresh = process.argv.includes('--refresh');

const cards = (await pool.query(`
  select card_id, min(name) as name
  from cards
  ${refresh ? '' : 'where estimated_unit_value is null'}
  group by card_id
  order by card_id
`)).rows;

let updated = 0;
let unavailable = 0;

try {
  for (const card of cards) {
    try {
      const details = await getCardDetails({ id: Number(card.card_id), name: card.name });
      const value = Number(details.prices?.min);
      if (!Number.isFinite(value) || value < 0) throw new Error('Cotacao indisponivel');
      const result = await pool.query(
        `update cards set estimated_unit_value = $1 where card_id = $2
         ${refresh ? '' : 'and estimated_unit_value is null'}`,
        [value, card.card_id]
      );
      updated += result.rowCount;
      console.log(`Cotacao salva: ${card.name} = R$ ${value.toFixed(2)} (${result.rowCount} inventarios)`);
    } catch (error) {
      unavailable++;
      console.warn(`Sem cotacao para ${card.name}: ${error.message}`);
    }
  }

  console.log({ distinctCards: cards.length, updated, unavailable });
} finally {
  await pool.end();
}
