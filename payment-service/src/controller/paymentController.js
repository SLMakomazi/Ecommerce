const express = require('express');
const { z } = require('zod');
const { request } = require('../dto/payment');
const service = require('../service/paymentService');
const { internal, authenticated } = require('../config/security');
const router = express.Router();
router.post('/internal/payments', internal, async (req, res) =>
  res.status(201).json(await service.create(request.parse(req.body))),
);
router.post('/internal/payments/:id/refund', internal, async (req, res) =>
  res.json(await service.refund(z.uuid().parse(req.params.id))),
);
router.get('/api/payments/me', authenticated, async (req, res) => {
  const page = z.coerce
    .number()
    .int()
    .min(0)
    .max(100000)
    .parse(req.query.page || 0);
  res.json(await service.list(req.customerId, page));
});
module.exports = router;
