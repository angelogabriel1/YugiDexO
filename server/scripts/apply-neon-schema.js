import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { pool } from '../src/db.js';

const here = dirname(fileURLToPath(import.meta.url));
const sql = await readFile(resolve(here, '../../neon/schema.sql'), 'utf8');

try {
  await pool.query(sql);
  const { rows } = await pool.query(`
    select
      current_database() as database,
      to_regclass('public.profiles') is not null as profiles,
      to_regclass('public.cards') is not null as cards,
      to_regclass('neon_auth.user') is not null as neon_auth
  `);
  console.log('Neon schema ready', rows[0]);
} finally {
  await pool.end();
}
