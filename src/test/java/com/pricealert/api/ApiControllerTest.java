package com.pricealert.api;

import com.pricealert.domain.product.*;
import com.pricealert.domain.store.Store;
import com.pricealert.integration.NotificationChannel;
import com.pricealert.repository.*;
import com.pricealert.service.MonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiControllerTest {
    @Test void rejectsSearchPagesEvenWhenQueryContainsProductIdentity() {
        assertThatThrownBy(()->api.track(new ApiController.TrackRequest(Store.MERCADO_LIVRE,"https://www.mercadolivre.com.br/search?item=MLB123",null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(tracked,never()).save(any());
    }
    final ProductRepository products=mock(ProductRepository.class);
    final PriceHistoryRepository history=mock(PriceHistoryRepository.class);
    final TrackedProductRepository tracked=mock(TrackedProductRepository.class);
    final MonitoringService monitoring=mock(MonitoringService.class);
    final NotificationChannel notifications=mock(NotificationChannel.class);
    final ApiController api=new ApiController(products,history,tracked,monitoring,notifications);

    @Test void rejectsInvalidPagesAndMissingProducts() {
        assertThatThrownBy(()->api.products(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->api.products(100001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->api.product(999L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->api.history(999L,0)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(history);
    }
    @Test void retrievesProductHistoryAndFreshDeals() {
        Product product=new Product(); when(products.findById(1L)).thenReturn(Optional.of(product));
        assertThat(api.product(1L)).isSameAs(product);
        api.history(1L,2); verify(history).findByProductId(eq(1L),argThat(p->p.getPageNumber()==2 && p.getPageSize()==50));
        api.deals(0); verify(products).findByDealTrueAndAvailableTrueAndLastCheckedAtAfter(any(),any(Pageable.class));
        api.tracked(0); verify(tracked).findAll(any(Pageable.class));
        api.products(0); verify(products).findAll(any(Pageable.class));
        assertThat(api.stores()).isNotNull(); assertThat(api.status()).isNotNull();
    }
    @Test void tracksValidProductAndDeletesTracking() {
        when(tracked.save(any())).thenAnswer(i->i.getArgument(0));
        var result=api.track(new ApiController.TrackRequest(Store.KABUM,"https://www.kabum.com.br/produto/123",new BigDecimal("100")));
        assertThat(result.store).isEqualTo(Store.KABUM); assertThat(result.targetPrice).isEqualByComparingTo("100");
        api.untrack(1L); verify(tracked).deleteById(1L);
    }
    @Test void rejectsCapacityOverflowAndPrivateApiUrls() {
        var request=new ApiController.TrackRequest(Store.KABUM,"https://www.kabum.com.br/produto/123",null);
        when(tracked.count()).thenReturn(200L);
        assertThatThrownBy(()->api.track(request)).isInstanceOf(ResponseStatusException.class);
        when(tracked.count()).thenReturn(0L);
        assertThatThrownBy(()->api.track(new ApiController.TrackRequest(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/items/MLB123",null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(tracked,never()).save(any());
    }
    @Test void errorsDoNotExposeInternalDetails() {
        var errors=new ApiErrors(); var response=errors.invalid(new IllegalArgumentException("private detail"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).doesNotContain("private detail");
        assertThat(errors.conflict().getStatusCode().value()).isEqualTo(409);
    }
}
