package com.example.lifecycle.bpp;

import com.example.lifecycle.support.Seq;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Mounts the two plain BeanPostProcessor hooks:
 * step 7 (before initialization) and step 10 (after initialization).
 *
 * <p>Step 7 is where {@code ApplicationContextAwareProcessor} injects the
 * context-layer Aware interfaces (7a) and {@code InitDestroyAnnotationBeanPostProcessor}
 * runs {@code @PostConstruct} (7b). Step 10 is where AOP weaving happens: the
 * auto-proxy creator may return a PROXY in place of the raw bean.
 *
 * <p>Ordered LOWEST so our marker prints just after the built-in processors of the
 * same phase have done their real work (so, e.g., our step-10 line lands right after
 * the proxy has actually been created).
 */
@Component
public class TrackingBeanPostProcessor implements BeanPostProcessor, Ordered {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("7", beanName, "postProcessBeforeInitialization boundary (context-Aware @ 7a + @PostConstruct @ 7b run here)");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            boolean proxied = bean.getClass().getName().contains("$$SpringCGLIB$$");
            Seq.step("10", beanName, "postProcessAfterInitialization boundary (AOP weaves here) -> returned "
                    + (proxied ? "a CGLIB PROXY: " + bean.getClass().getSimpleName() : "the raw bean"));
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
