package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Demonstrates step 12.
 *
 * <p>{@code afterSingletonsInstantiated()} is a CONTAINER-level callback, not part
 * of any single bean's chain. It fires once, after EVERY non-lazy singleton has
 * finished its own steps 0-11. This is where framework beans like
 * {@code EventListenerMethodProcessor} scan for {@code @EventListener} methods.
 */
@Component
public class AllSingletonsReadyReporter implements SmartInitializingSingleton {

    @Override
    public void afterSingletonsInstantiated() {
        Seq.step("12", "(container)", "SmartInitializingSingleton.afterSingletonsInstantiated() [ALL singletons ready]");
    }
}
