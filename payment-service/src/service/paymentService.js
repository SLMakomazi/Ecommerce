const repository = require('../repository/paymentRepository');
const { normalizedAmount, outcome } = require('../model/payment');
function failure(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}
function response(p) {
  return { orderId: p.order_id, amount: p.amount, currency: p.currency, status: p.status };
}
async function create(r) {
  const amount = normalizedAmount(r.amount);
  return repository.transaction(async (c) => {
    await repository.lock(c, r.orderId);
    let p = await repository.find(c, r.orderId);
    if (p) {
      if (
        p.customer_id !== r.customerId ||
        p.amount !== amount ||
        p.currency !== r.currency ||
        p.sandbox_token !== r.token
      )
        throw failure(409, 'Payment key reused with different details');
      return response(p);
    }
    p = (
      await c.query(
        'INSERT INTO payments(order_id,customer_id,amount,currency,status,sandbox_token) VALUES($1,$2,$3,$4,$5,$6) RETURNING *',
        [r.orderId, r.customerId, amount, r.currency, outcome(r.token), r.token],
      )
    ).rows[0];
    return response(p);
  });
}
async function refund(id) {
  return repository.transaction(async (c) => {
    await repository.lock(c, id);
    const p = await repository.find(c, id);
    if (!p) throw failure(404, 'Payment not found');
    if (p.status === 'REFUNDED') return response(p);
    if (p.status !== 'SUCCEEDED') throw failure(409, 'Only successful payments can be refunded');
    const result = (
      await c.query("UPDATE payments SET status='REFUNDED' WHERE order_id=$1 RETURNING *", [id])
    ).rows[0];
    return response(result);
  });
}
module.exports = { create, refund, list: repository.list };
