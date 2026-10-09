const pino = require('pino');
module.exports = pino({
  base: {
    service: 'notification-service',
    application: 'ecommerce',
    environment: process.env.MONITOR_ENVIRONMENT || 'local',
  },
  redact: ['authorization', 'token', 'address', 'phone', 'password'],
});
