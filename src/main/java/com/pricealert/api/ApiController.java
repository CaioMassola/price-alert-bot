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
@io.swagger.v3.oas.annotations.OpenAPIDefinition(info=@io.swagger.v3.oas.annotations.info.Info(
    title="Milizé — API de ofertas",version="1.0",description="Produtos, histórico de preços e acompanhamento de ofertas. Paginação de 50 registros, começando em zero."))
@io.swagger.v3.oas.annotations.tags.Tag(name="Ofertas",description="Consulta e acompanhamento de produtos")
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
    @io.swagger.v3.oas.annotations.Operation(summary="Listar produtos monitorados")
    @GetMapping("/products") public Page<Product> products(@RequestParam(defaultValue="0") int page) { return products.findAll(page(page)); }
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar um produto pelo ID")
    @GetMapping("/products/{id}") public Product product(@PathVariable Long id) {
        return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar histórico de preços de um produto")
    @GetMapping("/products/{id}/history") public Page<PriceHistory> history(@PathVariable Long id,@RequestParam(defaultValue="0") int page) {
        product(id); return history.findByProductId(id,page(page));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Listar ofertas disponíveis verificadas na última hora")
    @GetMapping("/deals") public Page<Product> deals(@RequestParam(defaultValue="0") int page) {
        return products.findByDealTrueAndAvailableTrueAndLastCheckedAtAfter(Instant.now().minus(Duration.ofHours(1)),page(page));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Listar acompanhamentos cadastrados")
    @GetMapping("/tracked-products") public Page<TrackedProduct> tracked(@RequestParam(defaultValue="0") int page) { return tracked.findAll(page(page)); }
    @io.swagger.v3.oas.annotations.Operation(summary="Cadastrar acompanhamento",description="URL HTTPS de produto de uma loja reconhecida; preço-alvo opcional. Limite de 200 acompanhamentos.")
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
    @io.swagger.v3.oas.annotations.Operation(summary="Remover acompanhamento")
    @DeleteMapping("/tracked-products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable Long id) { tracked.deleteById(id); }
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar resultado da coleta por loja")
    @GetMapping("/stores") public List<MonitoringService.StoreHealth> stores() { return monitoring.health(); }
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar configuração do Discord e estado das lojas")
    @GetMapping("/status") public Object status() {
        return Map.of("discordConfigured",notifications.configured(),"stores",monitoring.health());
    }
    public record TrackRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example="KABUM") @NotNull Store store,
        @io.swagger.v3.oas.annotations.media.Schema(description="URL HTTPS da página pública do produto",example="https://www.kabum.com.br/produto/123")
        @NotBlank @Size(max=2048) String url,
        @io.swagger.v3.oas.annotations.media.Schema(description="Preço-alvo opcional em reais",example="500.00")
        @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal targetPrice) {}
}

