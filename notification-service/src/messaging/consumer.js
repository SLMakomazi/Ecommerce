const amqp = require('amqplib');
const { event } = require('../dto/event');
const service = require('../service/notificationService');
const logger = require('../config/logger');
let ready = false;
let stopping = false;
let connection;
let timer;
async function connect() {
  if (stopping) return;
  try {
    const url = new URL('amqp://rabbitmq');
    url.hostname = process.env.RABBIT_HOST || 'rabbitmq';
    url.username = process.env.RABBIT_USER;
    url.password = process.env.RABBIT_PASSWORD;
    connection = await amqp.connect(url.toString(), { timeout: 3000 });
    connection.on('error', () => {});
    connection.on('close', () => {
      ready = false;
      schedule();
    });
    const channel = await connection.createConfirmChannel();
    channel.on('error', () => {});
    channel.on('close', () => {
      ready = false;
      connection.close().catch(() => {});
    });
    await channel.assertQueue('notifications.orders', { durable: true });
    await channel.assertQueue('notifications.dead', { durable: true });
    await channel.prefetch(10);
    await channel.consume('notifications.orders', async (msg) => {
      if (!msg) return;
      let parsed;
      try {
        parsed = event.parse(JSON.parse(msg.content.toString()));
      } catch {
        try {
          channel.sendToQueue('notifications.dead', msg.content, { persistent: true });
          await channel.waitForConfirms();
          channel.ack(msg);
          logger.warn('invalid_event_dead_lettered');
        } catch {
          try {
            channel.nack(msg, false, true);
          } catch {}
        }
        return;
      }
      try {
        await service.record(parsed);
        channel.ack(msg);
        logger.info(
          { order_id: parsed.orderId, event_id: parsed.eventId },
          'notification_recorded',
        );
      } catch {
        logger.warn({ event_id: parsed.eventId }, 'notification_retry');
        setTimeout(() => {
          try {
            channel.nack(msg, false, true);
          } catch {}
        }, 1000);
      }
    });
    ready = true;
    logger.info('consumer_connected');
  } catch {
    ready = false;
    schedule();
    logger.warn('consumer_connect_retry');
  }
}
function schedule() {
  clearTimeout(timer);
  if (!stopping) timer = setTimeout(connect, 2000);
}
process.once('SIGTERM', () => {
  stopping = true;
  clearTimeout(timer);
  connection?.close().catch(() => {});
});
module.exports = { start: connect, isReady: () => ready };
