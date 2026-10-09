const { z } = require('zod');
const request = z
  .object({
    orderId: z.uuid(),
    customerId: z.uuid(),
    amount: z.string().regex(/^(0|[1-9][0-9]{0,9})\.[0-9]{2}$/),
    currency: z.literal('ZAR'),
    token: z.enum(['sandbox-success', 'sandbox-decline']),
  })
  .strict();
module.exports = { request };
