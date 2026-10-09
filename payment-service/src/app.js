const { createApp, finish } = require('./config/http');
const app = createApp();
app.use(require('./controller/paymentController'));
finish(app);
