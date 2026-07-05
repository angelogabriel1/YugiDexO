import test from 'node:test';
import assert from 'node:assert/strict';
import { sseHub } from '../src/lib/sseHub.js';

test('SSE publica apenas para o duelista inscrito e remove conexao fechada', () => {
  const writes = [];
  const response = { write: chunk => writes.push(chunk) };
  const unsubscribe = sseHub.subscribe('Yugi', response);

  sseHub.publish('Kaiba');
  sseHub.publish('yugi');
  unsubscribe();
  sseHub.publish('YUGI');

  assert.equal(writes.length, 2);
  assert.match(writes[0], /event: connected/);
  assert.equal(writes[1], 'data: reload\n\n');
});
