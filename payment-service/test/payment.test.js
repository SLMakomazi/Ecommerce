const { test } = require('node:test');
const assert = require('node:assert/strict');
const { normalizedAmount, outcome } = require('../src/model/payment');
const { request } = require('../src/dto/payment');
test('money uses exact decimal representation', () => {
  assert.equal(normalizedAmount('0.10'), '0.10');
  assert.equal(normalizedAmount('99999999.99'), '99999999.99');
  assert.throws(() => normalizedAmount('0.00'));
  assert.throws(() => normalizedAmount('-1.00'));
});
test('payment input rejects floating amounts, extra fields and invalid tokens', () => {
  const valid = {
    orderId: crypto.randomUUID(),
    customerId: crypto.randomUUID(),
    amount: '10.50',
    currency: 'ZAR',
    token: 'sandbox-success',
  };
  assert.equal(request.parse(valid).amount, '10.50');
  assert.throws(() => request.parse({ ...valid, amount: '1.001' }));
  assert.throws(() => request.parse({ ...valid, amount: 10.5 }));
  assert.throws(() => request.parse({ ...valid, token: 'card-details' }));
  assert.throws(() => request.parse({ ...valid, extra: true }));
});
test('sandbox decline is explicit', () => assert.equal(outcome('sandbox-decline'), 'DECLINED'));
