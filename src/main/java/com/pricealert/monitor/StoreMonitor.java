package com.pricealert.monitor;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.product.ProductSnapshot;
import java.util.List;
public interface StoreMonitor {
    Store getStore();
    List<ProductSnapshot> searchProducts(MonitorRequest request);
}

