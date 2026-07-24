package com.example.lifecycle.bpp;

import com.example.lifecycle.support.Seq;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.stereotype.Component;

/**
 * Mounts step 2: post-processing of the merged bean definition.
 *
 * <p>This fires AFTER the object is instantiated (step 1') but BEFORE population
 * (steps 4/5). It is where the real annotation post-processors scan and CACHE the
 * injection-point metadata (which fields to @Autowire, which methods are
 * @PostConstruct/@PreDestroy). It only pre-computes; it injects nothing yet.
 */
@Component
public class TrackingMergedBeanDefinitionBPP implements MergedBeanDefinitionPostProcessor {

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (Seq.tracked(beanName)) {
            Seq.step("2", beanName, "postProcessMergedBeanDefinition (cache injection-point metadata; nothing injected yet)");
        }
    }
}
