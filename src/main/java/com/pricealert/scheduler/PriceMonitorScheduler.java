package com.pricealert.scheduler;
import com.pricealert.service.MonitoringService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class PriceMonitorScheduler {
    private final MonitoringService service;
    public PriceMonitorScheduler(MonitoringService service) { this.service=service; }
    @Scheduled(fixedDelayString="${monitor.tracked-products.interval}",initialDelayString="PT10S")
    public void tracked() { service.monitorTrackedProducts(); }
    @Scheduled(fixedDelayString="${monitor.promotions.interval}",initialDelayString="PT15S")
    public void promotions() { service.discoverPromotions(); }
    @Scheduled(fixedDelayString="PT5S",initialDelayString="PT20S")
    public void work() { service.runNext(); }
}

