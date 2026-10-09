CREATE TABLE carts(id UUID PRIMARY KEY,items TEXT NOT NULL);
CREATE TABLE purchase_orders(id UUID PRIMARY KEY,customer_id UUID NOT NULL,idempotency_key VARCHAR(100) NOT NULL,request_hash TEXT NOT NULL,items TEXT NOT NULL,snapshot TEXT,address VARCHAR(1000) NOT NULL,payment_token VARCHAR(100) NOT NULL,status VARCHAR(30) NOT NULL CHECK(status IN ('RECEIVED','RESERVED','PAID','CONFIRMED','DECLINED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED')),total NUMERIC(19,2),currency VARCHAR(3) NOT NULL CHECK(currency='ZAR'),created_at TIMESTAMPTZ NOT NULL,next_attempt TIMESTAMPTZ NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,failure_reason VARCHAR(255),UNIQUE(customer_id,idempotency_key));
CREATE INDEX orders_customer_idx ON purchase_orders(customer_id,created_at DESC);
CREATE INDEX orders_work_idx ON purchase_orders(next_attempt) WHERE status IN ('RECEIVED','RESERVED','PAID','DECLINED','REFUND_PENDING');
CREATE TABLE outbox_events(id UUID PRIMARY KEY,payload TEXT NOT NULL,published BOOLEAN NOT NULL DEFAULT false,created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX outbox_pending_idx ON outbox_events(created_at) WHERE published=false;
