const pool = require('../config/database');
async function save(event, message) {
  return pool.query(
    'INSERT INTO notifications(event_id,customer_id,order_id,type,message) VALUES($1,$2,$3,$4,$5) ON CONFLICT(event_id) DO NOTHING',
    [event.eventId, event.customerId, event.orderId, event.type, message],
  );
}
async function list(customer, page) {
  return (
    await pool.query(
      'SELECT event_id AS id,order_id AS "orderId",type,message,created_at AS "createdAt" FROM notifications WHERE customer_id=$1 ORDER BY created_at DESC LIMIT 20 OFFSET $2',
      [customer, page * 20],
    )
  ).rows;
}
module.exports = { save, list };
