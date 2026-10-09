package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.repository.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
  private final OutboxRepository repository;
  private final RabbitTemplate rabbit;
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  public OutboxPublisher(OutboxRepository repository, RabbitTemplate rabbit) {
    this.repository = repository;
    this.rabbit = rabbit;
  }

  @Bean
  Queue notificationsQueue() {
    return new Queue("notifications.orders", true);
  }

  @Transactional
  public void tick() {
    var events = repository.nextWork();
    if (events.isEmpty()) return;
    var e = events.getFirst();
    try {
      var props = new MessageProperties();
      props.setContentType("application/json");
      props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      props.setMessageId(e.getId().toString());
      var correlation = new CorrelationData(e.getId().toString());
      rabbit.send(
          "",
          "notifications.orders",
          new Message(e.getPayload().getBytes(StandardCharsets.UTF_8), props),
          correlation);
      var confirm =
          correlation
              .getFuture()
              .get(Duration.ofSeconds(4).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!confirm.ack() || correlation.getReturned() != null)
        throw new IllegalStateException("Event not delivered");
      e.setPublished(true);
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      log.warn("outbox_retry event_id={} exception={}", e.getId(), ex.getClass().getSimpleName());
    }
  }
}
