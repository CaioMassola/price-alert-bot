package com.pricealert;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import static org.mockito.Mockito.*;
class ApplicationStartupTest {
    @Test void forwardsStartupArgumentsToSpring() {
        String[] args={"--server.port=0"};
        try(var spring=mockStatic(SpringApplication.class)) {
            PriceAlertApplication.main(args);
            spring.verify(()->SpringApplication.run(PriceAlertApplication.class,args));
        }
    }
}
