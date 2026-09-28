package com.pricealert.service;
import com.pricealert.repository.*;
import com.pricealert.domain.alert.*;
import com.pricealert.integration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import org.slf4j.*;
@Service
public class AlertDeliveryService {
    private static final Logger log=LoggerFactory.getLogger(AlertDeliveryService.class);
    private final AlertRepository alerts;
    private final ProductRepository products;
    private final NotificationChannel channel;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private Instant nextDelivery=Instant.EPOCH;
    private Instant lastConfirmedDelivery=Instant.EPOCH;
    public AlertDeliveryService(AlertRepository alerts, ProductRepository products, NotificationChannel channel,
        ObjectMapper mapper, PlatformTransactionManager manager) {
        this.alerts=alerts; this.products=products; this.channel=channel; this.mapper=mapper;
        this.tx=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="PT10S",initialDelayString="PT20S")
    public synchronized void deliver() {
        if(!channel.configured() || nextDelivery.isAfter(Instant.now())) return;
        for(Alert alert:alerts.findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("PENDING",Instant.now())) {
            var current=products.findById(alert.productId).orElse(null);
            if(current==null || !current.available || current.currentPrice.compareTo(alert.price)!=0 ||
                alert.createdAt.isBefore(Instant.now().minus(Duration.ofHours(1)))) {
                alert.status="STALE"; alerts.save(alert); continue;
            }
            // Durable claim precedes the external side effect. SENDING after a crash requires manual review.
            tx.executeWithoutResult(status->{ alert.status="SENDING"; alert.attempts++; alerts.saveAndFlush(alert); });
            try {
                boolean introduction=lastConfirmedDelivery.plus(Duration.ofMinutes(15)).isBefore(Instant.now());
                channel.send(mapper.readValue(alert.payload,PriceAlert.class),introduction);
                lastConfirmedDelivery=Instant.now();
                tx.executeWithoutResult(status->{
                    alert.status="SENT"; alert.sentAt=Instant.now(); alert.deliveryError=null; alerts.save(alert);
                    products.findById(alert.productId).ifPresent(p->{ p.lastAlertPrice=alert.price; products.save(p); });
                });
                log.info("Alert sent id={} product={}",alert.id,alert.productId);
            } catch(NotificationException e) {
                alert.deliveryError=e.state;
                if("RATE_LIMITED".equals(e.state)) {
                    alert.status="PENDING"; alert.nextAttemptAt=e.retryAt; nextDelivery=e.retryAt; alerts.save(alert); return;
                }
                alert.status=e.state; alerts.save(alert);
                log.warn("Alert delivery requires review id={} state={}",alert.id,alert.status);
            } catch(Exception e) {
                alert.status="UNKNOWN"; alert.deliveryError="REQUIRES_REVIEW"; alerts.save(alert);
                log.warn("Alert delivery requires review id={}",alert.id);
            }
        }
    }
}

