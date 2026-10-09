const { z } = require('zod');
const event = z
  .object({
    eventId: z.uuid(),
    type: z.enum(['order.confirmed', 'order.rejected', 'order.refunded', 'order.cancelled']),
    orderId: z.uuid(),
    customerId: z.uuid(),
    status: z.enum(['CONFIRMED', 'REJECTED', 'REFUNDED', 'CANCELLED']),
    amount: z.string(),
    currency: z.literal('ZAR'),
  })
  .strict()
  .refine((e) => e.type === `order.${e.status.toLowerCase()}`, 'Event status mismatch');
module.exports = { event };
