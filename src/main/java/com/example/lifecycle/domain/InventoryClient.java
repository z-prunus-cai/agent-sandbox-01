package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import org.springframework.stereotype.Component;

/**
 * A field-injected (@Autowired) collaborator of {@link OrderService}.
 *
 * <p>Because it is injected into a FIELD (not the constructor), it is null at
 * OrderService's construction time (step 1') and only gets wired during property
 * population (step 5). That gap is the crux of the "you can't read @Autowired
 * fields inside a constructor" rule.
 */
@Component
public class InventoryClient {

    public InventoryClient() {
        Seq.step("1'", "inventoryClient", "constructed (a @Autowired field target, wired into OrderService at step 5)");
    }

    public boolean reserve(Order order) {
        return true;
    }
}
