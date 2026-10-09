const Decimal = require('decimal.js');
function normalizedAmount(value) {
  const amount = new Decimal(value);
  if (!amount.greaterThan(0) || amount.greaterThan('100000000.00')) {
    const e = new Error('Invalid amount');
    e.status = 400;
    throw e;
  }
  return amount.toFixed(2);
}
function outcome(token) {
  return token === 'sandbox-success' ? 'SUCCEEDED' : 'DECLINED';
}
module.exports = { normalizedAmount, outcome };
