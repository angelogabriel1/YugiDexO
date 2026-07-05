import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pool } from '../src/db.js';

const defaultName = `yugidex-database-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
const output = resolve(process.argv[2] || `backups/${defaultName}`);

try {
  const [profiles, cards, legacyAccounts] = await Promise.all([
    pool.query('select * from profiles order by created_at'),
    pool.query('select * from cards order by user_id, saved_at'),
    pool.query('select * from legacy_accounts order by created_at')
  ]);
  const backup = {
    createdAt: new Date().toISOString(),
    profiles: profiles.rows,
    cards: cards.rows,
    legacyAccounts: legacyAccounts.rows
  };
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(backup, null, 2), { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({ output, profiles: profiles.rowCount, cards: cards.rowCount, legacyAccounts: legacyAccounts.rowCount }));
} finally {
  await pool.end();
}
