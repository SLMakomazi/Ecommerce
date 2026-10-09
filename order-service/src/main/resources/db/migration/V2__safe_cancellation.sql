ALTER TABLE purchase_orders DROP CONSTRAINT purchase_orders_status_check;
ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_status_check
CHECK (status IN ('RECEIVED','RESERVED','PAID','CONFIRMED','DECLINED','REJECTED','CANCEL_PENDING','CANCELLED','REFUND_PENDING','REFUNDED'));
DROP INDEX orders_work_idx;
CREATE INDEX orders_work_idx ON purchase_orders(next_attempt)
WHERE status IN ('RECEIVED','RESERVED','PAID','DECLINED','REFUND_PENDING','CANCEL_PENDING');
