package com.example.ecommerce.order.service;

import com.example.ecommerce.order.messaging.OutboxPublisher;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerScheduler {
  private final CheckoutWorker checkout;
  private final OutboxPublisher outbox;
  private static final Logger log = LoggerFactory.getLogger(WorkerScheduler.class);

  public WorkerScheduler(CheckoutWorker checkout, OutboxPublisher outbox) {
    this.checkout = checkout;
    this.outbox = outbox;
  }

  @Scheduled(fixedDelay = 500)
  public void checkout() {
    try {
      checkout.tick();
    } catch (Exception e) {
      log.warn("checkout_worker_unavailable exception={}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelay = 1000)
  public void publish() {
    try {
      outbox.tick();
    } catch (Exception e) {
      log.warn("outbox_worker_unavailable exception={}", e.getClass().getSimpleName());
    }
  }
}
