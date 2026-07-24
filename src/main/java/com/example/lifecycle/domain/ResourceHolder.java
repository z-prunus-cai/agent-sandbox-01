package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;

/**
 * Demonstrates the D3 mutual-exclusion rule.
 *
 * <p>This bean is {@link AutoCloseable} AND is declared with an inferred destroy
 * method in {@code DemoConfig} ({@code @Bean(destroyMethod = "")} disables
 * inference; the default "(inferred)" enables it). Because it is AutoCloseable,
 * the adapter picks the {@code close()} branch. Even though a {@code customDestroy()}
 * method exists, it is NOT called: close wins.
 */
public class ResourceHolder implements AutoCloseable {

    public ResourceHolder() {
        Seq.step("1'", "resourceHolder", "constructed (AutoCloseable; will destroy via close(), not customDestroy())");
    }

    @Override
    public void close() {
        Seq.step("D3", "resourceHolder", "AutoCloseable.close() called by the adapter [close() WINS over destroy-method]");
    }

    public void customDestroy() {
        Seq.step("D3", "resourceHolder", "customDestroy() -- SHOULD NOT PRINT (close() took the branch)");
    }
}
