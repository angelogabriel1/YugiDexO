import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pool } from '../src/db.js';

const defaultName = `yugidex-database-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
const output = resolve(process.argv[2] || `backups/${defaultName}`);

try {
  const [profiles, cards, legacyAccounts, tableFlags] = await Promise.all([
    pool.query('select * from profiles order by created_at'),
    pool.query('select * from cards order by user_id, saved_at'),
    pool.query('select * from legacy_accounts order by created_at'),
    pool.query(`
      select
        to_regclass('public.decks') is not null as decks,
        to_regclass('public.deck_cards') is not null as deck_cards,
        to_regclass('public.affiliate_links') is not null as affiliate_links
    `)
  ]);
  const [decks, deckCards] = tableFlags.rows[0].decks && tableFlags.rows[0].deck_cards
    ? await Promise.all([
        pool.query('select * from decks order by user_id, updated_at'),
        pool.query('select * from deck_cards order by deck_id, added_at')
      ])
    : [{ rows: [], rowCount: 0 }, { rows: [], rowCount: 0 }];
  const affiliateLinks = tableFlags.rows[0].affiliate_links
    ? await pool.query('select * from affiliate_links order by updated_at desc, card_name')
    : { rows: [], rowCount: 0 };
  const backup = {
    createdAt: new Date().toISOString(),
    profiles: profiles.rows,
    cards: cards.rows,
    decks: decks.rows,
    deckCards: deckCards.rows,
    affiliateLinks: affiliateLinks.rows,
    legacyAccounts: legacyAccounts.rows
  };
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(backup, null, 2), { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({
    output, profiles: profiles.rowCount, cards: cards.rowCount,
    decks: decks.rowCount, deckCards: deckCards.rowCount,
    affiliateLinks: affiliateLinks.rowCount, legacyAccounts: legacyAccounts.rowCount
  }));
} finally {
  await pool.end();
}
