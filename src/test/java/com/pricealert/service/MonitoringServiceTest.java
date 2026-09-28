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
