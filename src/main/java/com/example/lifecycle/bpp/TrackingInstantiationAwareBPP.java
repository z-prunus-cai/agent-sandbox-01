package com.example.lifecycle.bpp;

import com.example.lifecycle.support.Seq;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * One custom BPP that mounts the SmartInstantiationAware hooks so we can SEE the
 * steps that the built-in post-processors perform silently:
 * step 0 (before instantiation), step 1 (constructor selection),
 * step 3 (early reference), step 4 (before population), step 5 (property population).
 *
 * <p>Every hook returns the neutral default (null / true / pvs-as-is) so it only
 * observes; it never changes behavior. Output is filtered to the tracked demo beans.
 */
@Component
public class TrackingInstantiationAwareBPP implements SmartInstantiationAwareBeanPostProcessor {

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("0", beanName, "postProcessBeforeInstantiation asked (returning null -> normal creation path)");
        }
        return null; // null = "I won't short-circuit"
    }

    @Override
    public java.lang.reflect.Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName)
            throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("1", beanName, "determineCandidateConstructors (constructor selection)");
        }
        return null; // null = "no opinion; let AutowiredAnnotationBeanPostProcessor decide"
    }

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("3", beanName, "getEarlyBeanReference INVOKED (a real circular reference is being resolved)");
        }
        return bean;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("4", beanName, "postProcessAfterInstantiation (true -> continue to property population)");
        }
        return true; // true = "go ahead and populate properties"
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        if (Seq.tracked(beanName)) {
            Seq.step("5", beanName, "postProcessProperties (DI point; @Autowired/@Value/@Resource wired around here)");
        }
        return pvs;
    }
}
