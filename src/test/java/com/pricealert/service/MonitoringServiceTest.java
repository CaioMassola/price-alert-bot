package com.pricealert.service;
import com.pricealert.TestSupport;
import com.pricealert.monitor.*;
import com.pricealert.domain.store.Store;
import com.pricealert.repository.TrackedProductRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MonitoringServiceTest {
    @Test void disabledDiscoveryAndReentrantRoundDoNotAddJobs() {
        var config=mock(com.pricealert.config.MonitorConfig.class);
        var monitor=mock(StoreMonitor.class); when(monitor.getStore()).thenReturn(Store.KABUM);
        var service=new MonitoringService(List.of(monitor),mock(TrackedProductRepository.class),mock(ProductService.class),config);
        service.discoverPromotions(); service.runNext(); verify(monitor,never()).searchProducts(any());
        when(config.promotionsEnabled()).thenReturn(true); when(config.queries()).thenReturn(List.of("one"));
        when(monitor.searchProducts(any())).thenAnswer(invocation->{service.discoverPromotions(); return List.of();});
        service.discoverPromotions(); service.runNext(); service.runNext();
        verify(monitor,times(1)).searchProducts(any());
    }
    @Test void schedulerPrioritizesTrackedProductsAndReportsFailures() {
        for(Store store:Store.values()) for(int code:new int[]{0,401,403,404,422,429,500,410}) {
            var monitor=mock(StoreMonitor.class); when(monitor.getStore()).thenReturn(store);
            when(monitor.searchProducts(any())).thenThrow(new StoreAccessException(code,"private detail"));
            var tracked=mock(TrackedProductRepository.class);
            var entry=new com.pricealert.domain.product.TrackedProduct(); entry.store=store; entry.url="https://example.test/product";
            when(tracked.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(entry));
            var service=new MonitoringService(List.of(monitor),tracked,mock(ProductService.class),TestSupport.config());
            var scheduler=new com.pricealert.scheduler.PriceMonitorScheduler(service);
            scheduler.promotions(); scheduler.tracked(); scheduler.tracked(); scheduler.work();
            verify(monitor).searchProducts(MonitorRequest.product(entry.url));
            var health=service.health().stream().filter(h->h!=null && h.store()==store).findFirst().orElseThrow();
            assertThat(health.error()).isEqualTo("HTTP_OR_PARSER_"+code);
            assertThat(health.detail()).isNotBlank().doesNotContain("private detail");
            assertThat(health.nextStep()).isNotBlank();
            doThrow(new IllegalStateException("private detail")).when(monitor).searchProducts(any());
            scheduler.work();
            assertThat(service.health().stream().filter(h->h!=null).findFirst().orElseThrow().status()).isEqualTo("ERROR");
        }
    }
    @Test void mixesFourQueriesAndDoesNotQueueAnotherRoundWhilePending() {
        var config=mock(com.pricealert.config.MonitorConfig.class);
        when(config.promotionsEnabled()).thenReturn(true);
        when(config.queries()).thenReturn(List.of("smartphone","ps5","teclado","notebook","xbox"));
        var monitor=mock(StoreMonitor.class);
        when(monitor.getStore()).thenReturn(Store.KABUM);
        when(monitor.searchProducts(any())).thenReturn(List.of());
        var service=new MonitoringService(List.of(monitor),mock(TrackedProductRepository.class),mock(ProductService.class),config);
        service.discoverPromotions(); service.discoverPromotions();
        for(int i=0;i<5;i++) service.runNext();
        var requests=org.mockito.ArgumentCaptor.forClass(MonitorRequest.class);
        verify(monitor,times(4)).searchProducts(requests.capture());
        assertThat(requests.getAllValues().stream().map(MonitorRequest::query)).containsExactly("smartphone","ps5","teclado","notebook");
        service.discoverPromotions(); service.runNext();
        verify(monitor).searchProducts(MonitorRequest.search("xbox"));
    }
    @Test void failureOfOneStoreDoesNotStopNextStore() {
        StoreMonitor failing=mock(StoreMonitor.class), working=mock(StoreMonitor.class);
        when(failing.getStore()).thenReturn(Store.MERCADO_LIVRE);
        when(working.getStore()).thenReturn(Store.KABUM);
        when(failing.searchProducts(any())).thenThrow(new StoreAccessException(403,"restricted"));
        when(working.searchProducts(any())).thenReturn(List.of(TestSupport.snapshot("100","200")));
        var products=mock(ProductService.class);
        var service=new MonitoringService(List.of(failing,working),mock(TrackedProductRepository.class),products,TestSupport.config());
        service.discoverPromotions(); service.runNext(); service.runNext();
        verify(products).accept(any(),isNull(),eq(false));
        assertThat(service.health().stream().filter(h->h!=null && h.store()==Store.MERCADO_LIVRE).findFirst().orElseThrow().status())
            .isEqualTo("UNAVAILABLE");
        var unavailable=service.health().stream().filter(h->h!=null && h.store()==Store.MERCADO_LIVRE).findFirst().orElseThrow();
        assertThat(unavailable.detail()).contains("Acesso negado");
        assertThat(unavailable.nextStep()).contains("MERCADO_LIVRE_ACCESS_TOKEN");
    }
}
