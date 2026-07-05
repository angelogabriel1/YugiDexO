import { pool } from '../src/db.js';

try {
  const { rows: [status] } = await pool.query(`
    select
      (select count(*) from public.profiles)::int as profiles,
      (select count(*) from public.cards)::int as cards,
      (select count(*) from neon_auth."user")::int as auth_users,
      (select count(*) from public.legacy_accounts where imported_at is null)::int as pending_legacy_accounts
  `);
  console.log('Neon status', status);
} finally {
  await pool.end();
}
