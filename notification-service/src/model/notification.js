function message(event) {
  return `Order ${event.orderId}: ${event.status.toLowerCase()}.`;
}
module.exports = { message };
