package com.pricealert.api;
import com.pricealert.domain.product.*;
import com.pricealert.domain.price.PriceHistory;
import com.pricealert.domain.store.Store;
import com.pricealert.repository.*;
import com.pricealert.service.MonitoringService;
import com.pricealert.integration.NotificationChannel;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiController {
    private final ProductRepository products;
    private final PriceHistoryRepository history;
    private final TrackedProductRepository tracked;
    private final MonitoringService monitoring;
    private final NotificationChannel notifications;
    public ApiController(ProductRepository products,PriceHistoryRepository history,TrackedProductRepository tracked,
        MonitoringService monitoring,NotificationChannel notifications) {
        this.products=products; this.history=history; this.tracked=tracked; this.monitoring=monitoring; this.notifications=notifications;
    }
    private Pageable page(int page) {
        if(page<0 || page>100000) throw new IllegalArgumentException("Pagina invalida");
        return PageRequest.of(page,50,Sort.by(Sort.Direction.DESC,"id"));
    }
    @GetMapping("/products") public Page<Product> products(@RequestParam(defaultValue="0") int page) { return products.findAll(page(page)); }
    @GetMapping("/products/{id}") public Product product(@PathVariable Long id) {
        return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @GetMapping("/products/{id}/history") public Page<PriceHistory> history(@PathVariable Long id,@RequestParam(defaultValue="0") int page) {
        product(id); return history.findByProductId(id,page(page));
    }
    @GetMapping("/deals") public Page<Product> deals(@RequestParam(defaultValue="0") int page) {
        return products.findByDealTrueAndAvailableTrueAndLastCheckedAtAfter(Instant.now().minus(Duration.ofHours(1)),page(page));
    }
    @GetMapping("/tracked-products") public Page<TrackedProduct> tracked(@RequestParam(defaultValue="0") int page) { return tracked.findAll(page(page)); }
    @PostMapping("/tracked-products") @ResponseStatus(HttpStatus.CREATED)
    public TrackedProduct track(@Valid @RequestBody TrackRequest request) {
        if(tracked.count()>=200) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var uri=request.store().validateUrl(request.url());
        // Require a product-shaped URL, not arbitrary API resources or search pages.
        com.pricealert.monitor.StructuredProductParser.identity(request.store(),uri.toString());
        if("api.mercadolibre.com".equals(uri.getHost()) || uri.getPath().equals("/search"))
            throw new IllegalArgumentException("Informe a pagina publica de um produto");
        TrackedProduct entry=new TrackedProduct(); entry.store=request.store(); entry.url=uri.toString(); entry.targetPrice=request.targetPrice();
        return tracked.save(entry);
    }
    @DeleteMapping("/tracked-products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable Long id) { tracked.deleteById(id); }
    @GetMapping("/stores") public Object stores() { return monitoring.health(); }
    @GetMapping("/status") public Object status() {
        return Map.of("discordConfigured",notifications.configured(),"stores",monitoring.health());
    }
    public record TrackRequest(@NotNull Store store,@NotBlank @Size(max=2048) String url,
        @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal targetPrice) {}
}

