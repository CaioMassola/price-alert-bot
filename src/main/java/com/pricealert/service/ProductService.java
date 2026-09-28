package com.pricealert.service;
import com.pricealert.domain.product.*;
import com.pricealert.domain.alert.PriceAlert;
import com.pricealert.domain.coupon.*;
import com.pricealert.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.slf4j.*;
@Service
public class ProductService {
    private static final Logger log=LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository products;
    private final PriceHistoryRepository history;
    private final CouponRepository coupons;
    private final DiscountService discounts;
    private final PriceHistoryService prices;
    private final CouponService couponService;
    private final AlertService alerts;
    public ProductService(ProductRepository products, PriceHistoryRepository history, CouponRepository coupons,
        DiscountService discounts, PriceHistoryService prices, CouponService couponService, AlertService alerts) {
        this.products=products; this.history=history; this.coupons=coupons; this.discounts=discounts;
        this.prices=prices; this.couponService=couponService; this.alerts=alerts;
    }
    @Transactional
    public void accept(ProductSnapshot snapshot, BigDecimal target, boolean tracked) {
        Product old=products.findByStoreAndExternalId(snapshot.store(),snapshot.externalId()).orElse(null);
        var observations=old==null?new ArrayList<com.pricealert.domain.price.PriceHistory>():
            new ArrayList<>(history.findByProductIdOrderByCollectedAtAsc(old.id));
        var analysis=discounts.analyze(old,snapshot,observations,target,tracked);
        Product product=old==null?new Product():old;
        if(old==null) { product.store=snapshot.store(); product.externalId=snapshot.externalId(); product.createdAt=snapshot.collectedAt(); }
        product.name=snapshot.name(); product.url=snapshot.url(); product.imageUrl=snapshot.imageUrl();
        products.saveAndFlush(product);
        alerts.enqueue(product,new PriceAlert(product.id,snapshot,analysis,couponService.effectivePrice(snapshot.currentPrice(),snapshot.coupon(),snapshot.collectedAt())));
        if(product.currentPrice==null || product.currentPrice.compareTo(snapshot.currentPrice())!=0) {
            product.previousPrice=product.currentPrice;
            log.info("Price observed store={} product={} price={} historicalLow={}", snapshot.store(),product.id,snapshot.currentPrice(),analysis.newHistoricalLow());
        }
        product.currentPrice=snapshot.currentPrice(); product.originalPrice=snapshot.originalPrice();
        product.storeDiscount=analysis.storeDiscount(); product.historicalDiscount=analysis.historicalDiscount();
        product.available=snapshot.available(); product.deal=analysis.shouldAlert();
        product.couponCode=snapshot.coupon()==null?null:snapshot.coupon().code();
        product.updatedAt=snapshot.collectedAt(); product.lastCheckedAt=snapshot.collectedAt();
        prices.record(product,snapshot,analysis,observations);
        if(snapshot.coupon()!=null) saveCoupon(product.id,snapshot.coupon());
        products.save(product);
    }
    private void saveCoupon(Long productId, Coupon coupon) {
        CouponEntity entity=coupons.findByProductIdAndCode(productId,coupon.code()).orElseGet(CouponEntity::new);
        entity.productId=productId; entity.code=coupon.code(); entity.discountPercentage=coupon.discountPercentage();
        entity.discountValue=coupon.discountValue(); entity.minimumPurchase=coupon.minimumPurchase();
        entity.expiresAt=coupon.expiresAt(); entity.source=coupon.source(); coupons.save(entity);
    }
}

