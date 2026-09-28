package com.pricealert.config;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
@ConfigurationProperties("monitor")
@Validated
public record MonitorConfig(
    @NotNull Duration requestGap, @NotNull Duration timeout,
    @NotNull Duration cooldown, @NotNull Duration historyMinimumAge,
    @DecimalMin("0") @DecimalMax("100") BigDecimal minStoreDiscount,
    @DecimalMin("0") @DecimalMax("100") BigDecimal minHistoricalDiscount,
    @DecimalMin("0") @DecimalMax("100") BigDecimal trackedDrop,
    @Min(2) int minimumSamples, @Min(1) @Max(20) int maxProducts,
    boolean promotionsEnabled, @NotEmpty List<@NotBlank @Size(max=100) String> queries) {
    @AssertTrue(message="Intervalos devem ser positivos; request-gap pode ser zero apenas para testes")
    public boolean isTimingValid() {
        return requestGap!=null && !requestGap.isNegative() &&
            timeout!=null && timeout.isPositive() && cooldown!=null && cooldown.isPositive() &&
            historyMinimumAge!=null && historyMinimumAge.isPositive();
    }
}

