package com.pricealert.service;
import com.pricealert.TestSupport;
import com.pricealert.domain.product.Product;
import com.pricealert.domain.price.PriceHistory;
import com.pricealert.repository.PriceHistoryRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PriceHistoryServiceTest {
    @Test void ignoresFiveMinuteRepeatsButKeepsDailyBaseline() {
        var repository=mock(PriceHistoryRepository.class); var service=new PriceHistoryService(repository);
        Product p=new Product(); p.id=1L;
        var snapshot=TestSupport.snapshot("100","120");
        var analysis=new DiscountService(TestSupport.config()).analyze(null,snapshot,List.of(),null,false);
        var rows=new ArrayList<PriceHistory>();
        service.record(p,snapshot,analysis,rows); service.record(p,snapshot,analysis,rows);
        verify(repository,times(1)).save(any());
        rows.getFirst().collectedAt=Instant.now().minus(Duration.ofDays(1));
        service.record(p,snapshot,analysis,rows);
        verify(repository,times(2)).save(any()); assertThat(p.averagePrice).isEqualByComparingTo("100");
    }
}

