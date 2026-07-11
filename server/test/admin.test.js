import test from 'node:test';
import assert from 'node:assert/strict';
import { isAdminUser } from '../src/middleware/admin.js';

test('reconhece administrador por email, id ou username configurado', () => {
  const user = { id: 'user_123', email: 'Admin@Yugidex.com', name: 'farao' };

  assert.equal(isAdminUser(user, { ADMIN_EMAILS: 'admin@yugidex.com' }), true);
  assert.equal(isAdminUser(user, { ADMIN_USER_IDS: 'user_123' }), true);
  assert.equal(isAdminUser(user, { ADMIN_USERNAMES: 'farao' }), true);
});

test('nega admin quando nenhuma regra ou nenhuma correspondencia existe', () => {
  const user = { id: 'user_123', email: 'duelista@yugidex.com', name: 'duelista' };

  assert.equal(isAdminUser(user, {}), false);
  assert.equal(isAdminUser(user, { ADMIN_EMAILS: 'admin@yugidex.com' }), false);
});
