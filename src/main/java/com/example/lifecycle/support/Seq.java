package com.example.lifecycle.support;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny logging helper that stamps every lifecycle callback with a global,
 * monotonically increasing sequence number. The sequence number is the whole
 * point: it turns "these callbacks happen in some order" into a printed,
 * reproducible fact you can read top-to-bottom.
 *
 * <p>Our custom BeanPostProcessors see EVERY bean in the container (dozens of
 * Spring-internal infrastructure beans). To keep the transcript readable we
 * only print for the handful of demo beans listed in {@link #TRACKED}.
 */
public final class Seq {

    private Seq() {
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** Only these bean names produce output from the tracking post-processors. */
    public static final Set<String> TRACKED = Set.of(
            "orderService",
            "paymentGateway",
            "inventoryClient",
            "circularA",
            "circularB",
            "shortCircuitBean",
            "prototypeBean",
            "resourceHolder");

    public static boolean tracked(String beanName) {
        return TRACKED.contains(beanName);
    }

    /** A numbered lifecycle step, tied to Table A's step id (e.g. "5", "7b", "D1"). */
    public static void step(String stepId, String bean, String message) {
        System.out.printf("%02d | STEP %-3s | %-16s | %s%n",
                COUNTER.incrementAndGet(), stepId, bean, message);
    }

    /** A line with no Table-A step id (driver actions, aspect advice, notes). */
    public static void note(String bean, String message) {
        System.out.printf("%02d | %-8s | %-16s | %s%n",
                COUNTER.incrementAndGet(), "--", bean, message);
    }

    /** A phase banner separating container start / running / shutdown. */
    public static void banner(String message) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("==  " + message);
        System.out.println("======================================================================");
    }
}
