package com.example.lifecycle.bpp;

import com.example.lifecycle.support.Seq;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Mounts step D1: the before-destruction hook.
 *
 * <p>This is the same slot the built-in {@code InitDestroyAnnotationBeanPostProcessor}
 * uses to run {@code @PreDestroy}. Our marker confirms the destruction chain is
 * entered for a bean, and (by its absence) that prototypes and short-circuited
 * beans never get here.
 */
@Component
public class TrackingDestructionAwareBPP implements DestructionAwareBeanPostProcessor {

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("D1", beanName, "postProcessBeforeDestruction boundary (@PreDestroy runs here)");
        }
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        return true;
    }
}
