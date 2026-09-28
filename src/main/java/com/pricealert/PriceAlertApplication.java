package com.pricealert;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.pricealert.config.MonitorConfig;
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MonitorConfig.class)
@org.springframework.data.web.config.EnableSpringDataWebSupport(pageSerializationMode =
    org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class PriceAlertApplication {
    public static void main(String[] args) { SpringApplication.run(PriceAlertApplication.class, args); }
}

