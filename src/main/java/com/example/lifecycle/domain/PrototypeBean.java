package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A prototype-scoped bean, to prove the asymmetry the note calls out:
 * Spring runs INITIALIZATION for prototypes (so @PostConstruct fires), but it
 * never registers them for DESTRUCTION (step 11 is guarded by
 * {@code !mbd.isPrototype()}). So @PreDestroy here MUST NEVER print on shutdown.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeBean {

    @PostConstruct
    public void init() {
        Seq.step("7b", "prototypeBean", "@PostConstruct RAN (prototypes are still initialized)");
    }

    @PreDestroy
    public void cleanup() {
        Seq.step("D1", "prototypeBean", "@PreDestroy -- SHOULD NEVER PRINT (prototypes are not tracked for destruction)");
    }
}
