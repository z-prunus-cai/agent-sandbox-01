package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Demonstrates step 0's short-circuit path.
 *
 * <p>A custom {@code InstantiationAwareBeanPostProcessor} ({@code ShortCircuitBPP})
 * returns a ready-made instance from {@code postProcessBeforeInstantiation}. When
 * that happens, {@code doCreateBean} is skipped ENTIRELY: no constructor injection,
 * no property population, no @PostConstruct, and no destruction registration.
 * Only the after-initialization chain (step 0b) runs.
 *
 * <p>Therefore both the constructor marker below AND the @PostConstruct/@PreDestroy
 * must NOT print for the short-circuited instance.
 */
@Component
public class ShortCircuitBean {

    private final String origin;

    // Default constructor the container would normally call -- but won't, because
    // the BPP hands back a pre-built instance before instantiation.
    public ShortCircuitBean() {
        this.origin = "container";
        Seq.step("1'", "shortCircuitBean", "default constructor -- SHOULD NOT PRINT (short-circuited at step 0)");
    }

    public ShortCircuitBean(String origin) {
        this.origin = origin;
    }

    public String origin() {
        return origin;
    }

    @PostConstruct
    public void init() {
        Seq.step("7b", "shortCircuitBean", "@PostConstruct -- SHOULD NOT PRINT (init chain skipped after short-circuit)");
    }

    @PreDestroy
    public void cleanup() {
        Seq.step("D1", "shortCircuitBean", "@PreDestroy -- SHOULD NOT PRINT (never registered for destruction)");
    }
}
