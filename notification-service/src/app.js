const consumer = require('./messaging/consumer');
const { createApp, finish } = require('./config/http');
const app = createApp(consumer.isReady);
app.use(require('./controller/notificationController'));
finish(app);
consumer.start();
