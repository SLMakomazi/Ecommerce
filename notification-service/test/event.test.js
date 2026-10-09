const { test } = require('node:test');
const assert = require('node:assert/strict');
const { event } = require('../src/dto/event');
test('event requires identity and consistent state', () => {
  const valid = {
    eventId: crypto.randomUUID(),
    orderId: crypto.randomUUID(),
    customerId: crypto.randomUUID(),
    type: 'order.confirmed',
    status: 'CONFIRMED',
    amount: '1.00',
    currency: 'ZAR',
  };
  assert.equal(event.parse(valid).status, 'CONFIRMED');
  assert.throws(() => event.parse({ ...valid, status: 'REJECTED' }));
  assert.throws(() => event.parse({ ...valid, eventId: '' }));
});
