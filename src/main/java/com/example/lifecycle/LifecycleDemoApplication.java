package com.example.lifecycle;

import com.example.lifecycle.domain.Order;
import com.example.lifecycle.domain.OrderService;
import com.example.lifecycle.domain.PrototypeBean;
import com.example.lifecycle.support.Seq;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Drives the whole tour and narrates the three container phases.
 *
 * <p>We deliberately do NOT use a CommandLineRunner: we want to control exactly
 * when the container is "running" versus "closing", and print a banner around
 * {@code ctx.close()} so the destruction steps (D1-D3) are unmistakably separated
 * from startup.
 */
@SpringBootApplication
public class LifecycleDemoApplication {

    public static void main(String[] args) {
        Seq.banner("PHASE 1: CONTAINER STARTING  (refresh -> steps 0..13 for every non-lazy singleton)");
        ConfigurableApplicationContext ctx = SpringApplication.run(LifecycleDemoApplication.class, args);

        Seq.banner("PHASE 2: APPLICATION RUNNING (all singletons ready; beans handed to callers)");

        OrderService orderService = ctx.getBean(OrderService.class);
        Seq.note("driver", "ctx.getBean(OrderService) returned: " + orderService.getClass().getName());
        Seq.note("driver", "  -> is it a CGLIB proxy? " + orderService.getClass().getName().contains("$$SpringCGLIB$$"));

        Seq.note("driver", "calling orderService.placeOrder(...) through the injected reference:");
        orderService.placeOrder(new Order("A-1", 9_900));

        // Prototype: proves full initialization but zero destruction bookkeeping.
        Seq.note("driver", "requesting two prototype instances:");
        PrototypeBean p1 = ctx.getBean(PrototypeBean.class);
        PrototypeBean p2 = ctx.getBean(PrototypeBean.class);
        Seq.note("driver", "  same instance? " + (p1 == p2) + " (prototypes are fresh each getBean)");

        Seq.banner("PHASE 3: CONTAINER CLOSING   (ctx.close() -> SmartLifecycle.stop, then D1..D3)");
        ctx.close();

        Seq.banner("DONE. Note which 'SHOULD NOT PRINT' lines are absent -- that absence is the evidence.");
    }
}
