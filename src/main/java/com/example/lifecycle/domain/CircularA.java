package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Half of a circular dependency: A needs B, B needs A (field injection).
 *
 * <p>With {@code spring.main.allow-circular-references=true}, Spring breaks the
 * cycle using the three-level cache: while A is still half-built, it exposes an
 * EARLY reference of itself (step 3, {@code getEarlyBeanReference}) so that B can
 * be wired. That early-reference callback only fires when a real cycle is present,
 * which is exactly why the note says step 3 is "circular references only".
 */
@Component
public class CircularA {

    @Autowired
    private CircularB b;

    public CircularA() {
        Seq.step("1'", "circularA", "constructed (first of the A<->B cycle)");
    }

    @PostConstruct
    public void init() {
        Seq.step("7b", "circularA", "@PostConstruct: b is wired? " + (b != null));
    }
}
