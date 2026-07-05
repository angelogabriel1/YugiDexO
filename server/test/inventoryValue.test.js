import test from 'node:test';
import assert from 'node:assert/strict';
import { inventoryValue, pricedQuantity } from '../src/lib/inventoryValue.js';

test('calcula valor aproximado considerando quantidade e ignora cartas sem cotacao', () => {
  const cards = [
    { estimated_unit_value: 12.5, quantity: 2 },
    { estimatedUnitValue: 3, quantity: 4 },
    { estimated_unit_value: null, quantity: 9 }
  ];

  assert.equal(inventoryValue(cards), 37);
  assert.equal(pricedQuantity(cards), 6);
});

test('ignora valores e quantidades invalidos', () => {
  const cards = [
    { estimated_unit_value: -1, quantity: 2 },
    { estimated_unit_value: 'invalido', quantity: 2 },
    { estimated_unit_value: 10, quantity: 0 }
  ];

  assert.equal(inventoryValue(cards), 0);
  assert.equal(pricedQuantity(cards), 0);
});
