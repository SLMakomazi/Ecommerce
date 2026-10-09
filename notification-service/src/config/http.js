const express = require('express');
const prom = require('prom-client');
const pool = require('./database');
const logger = require('./logger');
const registry = new prom.Registry();
registry.setDefaultLabels({
  service: 'notification-service',
  application: 'ecommerce',
  environment: process.env.MONITOR_ENVIRONMENT || 'local',
});
prom.collectDefaultMetrics({ register: registry });
const duration = new prom.Histogram({
  name: 'http_server_requests_seconds',
  help: 'HTTP duration',
  labelNames: ['method', 'uri', 'status'],
  buckets: [0.01, 0.05, 0.1, 0.5, 1, 5],
  registers: [registry],
});
function createApp(extraReadiness = () => true) {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '32kb' }));
  app.use((req, res, next) => {
    const start = process.hrtime.bigint();
    res.on('finish', () => {
      if (req.path.startsWith('/actuator') || req.path === '/metrics') return;
      const labels = {
        method: req.method,
        uri: req.route?.path || 'unmatched',
        status: String(res.statusCode),
      };
      duration.observe(labels, Number(process.hrtime.bigint() - start) / 1e9);
      logger.info(
        { method: req.method, route: labels.uri, status: res.statusCode },
        'http_request',
      );
    });
    next();
  });
  app.get('/actuator/health/liveness', (req, res) => res.json({ status: 'UP' }));
  const readiness = async (req, res) => {
    try {
      await pool.query('SELECT 1');
      if (!extraReadiness()) throw new Error('Dependency not ready');
      res.json({ status: 'UP' });
    } catch {
      res.status(503).json({ status: 'DOWN' });
    }
  };
  app.get('/actuator/health', readiness);
  app.get('/actuator/health/readiness', readiness);
  const metrics = async (req, res) => {
    res.type(registry.contentType).send(await registry.metrics());
  };
  app.get('/metrics', metrics);
  app.get('/actuator/prometheus', metrics);
  return app;
}
function finish(app) {
  app.use((req, res) => res.status(404).json({ title: 'Not found', status: 404 }));
  app.use((err, req, res, next) => {
    if (err.name === 'ZodError' || err.type === 'entity.parse.failed')
      return res.status(400).json({ title: 'Request validation failed', status: 400 });
    if (err.status) return res.status(err.status).json({ title: err.message, status: err.status });
    logger.error({ exception: err.name }, 'request_failed');
    res.status(500).json({ title: 'Internal server error', status: 500 });
  });
  const server = app.listen(3000, () => logger.info('service_started'));
  const shutdown = () => server.close(() => pool.end().then(() => process.exit(0)));
  process.once('SIGTERM', shutdown);
  process.once('SIGINT', shutdown);
}
module.exports = { createApp, finish, registry };
