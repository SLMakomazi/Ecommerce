const express = require('express');
const { z } = require('zod');
const { authenticated } = require('../config/security');
const service = require('../service/notificationService');
const router = express.Router();
router.get('/api/notifications/me', authenticated, async (req, res) => {
  const page = z.coerce
    .number()
    .int()
    .min(0)
    .max(100000)
    .parse(req.query.page || 0);
  res.json(await service.list(req.customerId, page));
});
module.exports = router;
