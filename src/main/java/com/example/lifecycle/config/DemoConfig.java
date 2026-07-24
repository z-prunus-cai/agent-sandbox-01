package com.example.lifecycle.config;

import com.example.lifecycle.domain.OrderService;
import com.example.lifecycle.domain.PaymentGateway;
import com.example.lifecycle.domain.ResourceHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * We register {@link OrderService} and {@link ResourceHolder} via {@code @Bean}
 * (rather than component-scanning them) because {@code initMethod}/{@code destroyMethod}
 * can only be set through the {@code @Bean} attributes -- and those attributes are
 * what let us demonstrate steps 9 and D3.
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DemoConfig {

    /**
     * initMethod -> step 9 (runs AFTER InitializingBean.afterPropertiesSet, step 8).
     * destroyMethod -> step D3 (runs AFTER DisposableBean.destroy(), step D2).
     */
    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public OrderService orderService(PaymentGateway gateway) {
        return new OrderService(gateway);
    }

    /**
     * No explicit destroyMethod -> Spring INFERS one. Because ResourceHolder is
     * AutoCloseable, the inferred method is close(); the adapter takes the close()
     * branch, so its customDestroy() is never called (D3 mutual exclusion).
     */
    @Bean
    public ResourceHolder resourceHolder() {
        return new ResourceHolder();
    }
}
