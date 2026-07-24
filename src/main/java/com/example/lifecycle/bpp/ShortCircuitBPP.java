package com.example.lifecycle.bpp;

import com.example.lifecycle.domain.ShortCircuitBean;
import com.example.lifecycle.support.Seq;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * The step-0 short-circuit itself.
 *
 * <p>For the single bean {@code shortCircuitBean}, this returns a ready-made
 * instance from {@code postProcessBeforeInstantiation}. That non-null return makes
 * {@code AbstractAutowireCapableBeanFactory#resolveBeforeInstantiation} skip the
 * entire {@code doCreateBean} path -- only the after-initialization chain (step 0b)
 * still runs. So the target's constructor, injection, @PostConstruct, and
 * destruction registration are all bypassed.
 *
 * <p>Ordered HIGHEST so it wins the before-instantiation race for its bean.
 */
@Component
public class ShortCircuitBPP implements InstantiationAwareBeanPostProcessor, Ordered {

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if ("shortCircuitBean".equals(beanName)) {
            Seq.step("0", beanName, "postProcessBeforeInstantiation RETURNS a ready object -> SHORT-CIRCUIT");
            return new ShortCircuitBean("built-by-BPP-not-by-container");
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
