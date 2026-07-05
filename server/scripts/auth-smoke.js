import crypto from 'node:crypto';
import { app } from '../src/app.js';
import { pool } from '../src/db.js';

const suffix = `${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
const email = `yugidex-smoke-${suffix}@example.com`;
const username = `smoke_${crypto.randomBytes(4).toString('hex')}`;
const requestedUsername = `novo_${crypto.randomBytes(4).toString('hex')}`;
const password = `${crypto.randomBytes(18).toString('base64url')}Aa1!`;
let userId;
let server;
const baseUrlArgument = process.argv.find(argument => argument.startsWith('--base-url='));
const remoteBaseUrl = (process.env.SMOKE_BASE_URL || baseUrlArgument?.slice('--base-url='.length))?.replace(/\/$/, '');

async function request(baseUrl, path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, options);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(`${path} retornou ${response.status}: ${JSON.stringify(data)}`);
  return data;
}

try {
  await pool.query(`
    insert into legacy_accounts(email, legacy_user_id, username, cards)
    values ($1, $2, $3, $4::jsonb)
  `, [email, `smoke-${suffix}`, username, JSON.stringify([{
    card_id: 89631139,
    name: 'Blue-Eyes White Dragon',
    quantity: 1,
    saved_at: new Date().toISOString()
  }])]);
  if (!remoteBaseUrl) {
    server = app.listen(0, '127.0.0.1');
    await new Promise((resolve, reject) => {
      server.once('listening', resolve);
      server.once('error', reject);
    });
  }
  const baseUrl = remoteBaseUrl || `http://127.0.0.1:${server.address().port}`;
  const register = await request(baseUrl, '/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, username: requestedUsername, password })
  });
  if (register.username !== username || register.restoredCards !== 1) {
    throw new Error('A conta legada nao preservou username e inventario');
  }
  const userResult = await pool.query('select id from neon_auth."user" where lower(email) = lower($1)', [email]);
  userId = userResult.rows[0]?.id;
  if (!userId) throw new Error('O usuario de teste nao foi persistido no Neon Auth');
  if (!register.token) throw new Error('O cadastro funcionou, mas nenhum bearer token foi retornado');
  const sessionResult = await pool.query(`
    select length(token)::int as stored_length, token = $2 as matches
    from neon_auth.session where "userId" = $1 order by "createdAt" desc limit 1
  `, [userId, register.token]);
  console.log('Token diagnostic', {
    returnedLength: register.token.length,
    storedLength: sessionResult.rows[0]?.stored_length,
    matchesStoredSession: sessionResult.rows[0]?.matches ?? false,
    looksSigned: register.token.includes('.')
  });

  const sync = await request(baseUrl, '/api/cards/sync', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${register.token}` },
    body: JSON.stringify({ cards: [{ cardId: 89631139, name: 'Blue-Eyes White Dragon', quantity: 1 }] })
  });
  const collection = await request(baseUrl, `/api/collections/${username}`);
  const inventory = await request(baseUrl, '/api/cards', {
    headers: { Authorization: `Bearer ${register.token}` }
  });
  if (sync.synced !== 1 || collection.cards?.length !== 1 || inventory.cards?.length !== 1) {
    throw new Error('A sincronizacao de teste nao retornou uma carta');
  }
  await request(baseUrl, '/api/auth/logout', {
    method: 'POST',
    headers: { Authorization: `Bearer ${register.token}` }
  });
  const afterLogout = await fetch(`${baseUrl}/api/cards`, {
    headers: { Authorization: `Bearer ${register.token}` }
  });
  if (afterLogout.status !== 401) throw new Error('O logout nao invalidou a sessao');
  console.log('Smoke test OK', {
    target: remoteBaseUrl ? 'production' : 'local',
    registered: true,
    authenticated: true,
    synced: 1,
    inventoryDownloaded: true,
    publicCollection: true,
    logoutInvalidatedSession: true
  });
} finally {
  if (server) await new Promise(resolve => server.close(resolve));
  if (!userId) {
    const found = await pool.query('select id from neon_auth."user" where lower(email) = lower($1)', [email]);
    userId = found.rows[0]?.id;
  }
  if (userId) {
    await pool.query('delete from public.profiles where id = $1', [userId]);
    await pool.query('delete from neon_auth.session where "userId" = $1', [userId]);
    await pool.query('delete from neon_auth.account where "userId" = $1', [userId]);
    await pool.query('delete from neon_auth.member where "userId" = $1', [userId]);
    await pool.query('delete from neon_auth."user" where id = $1', [userId]);
  }
  await pool.query('delete from public.legacy_accounts where email = $1', [email]);
  await pool.end();
}
