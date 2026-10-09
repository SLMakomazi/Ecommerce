const pool = require('../config/database');
async function transaction(fn) {
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const result = await fn(c);
    await c.query('COMMIT');
    return result;
  } catch (e) {
    await c.query('ROLLBACK');
    throw e;
  } finally {
    c.release();
  }
}
async function lock(c, id) {
  await c.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))', [id]);
}
async function find(c, id) {
  return (await c.query('SELECT * FROM payments WHERE order_id=$1', [id])).rows[0];
}
async function list(customer, page) {
  return (
    await pool.query(
      'SELECT order_id AS "orderId",amount,currency,status,created_at AS "createdAt" FROM payments WHERE customer_id=$1 ORDER BY created_at DESC LIMIT 20 OFFSET $2',
      [customer, page * 20],
    )
  ).rows;
}
module.exports = { transaction, lock, find, list };
