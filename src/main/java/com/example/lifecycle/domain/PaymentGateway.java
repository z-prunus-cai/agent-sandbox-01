package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import org.springframework.stereotype.Component;

/**
 * A constructor-injected collaborator of {@link OrderService}.
 *
 * <p>Its only job in this lab is to prove step 1: when the container is about to
 * {@code new OrderService(paymentGateway)}, it must fully create THIS bean first.
 * So {@code paymentGateway}'s own 17-step chain runs to completion before
 * OrderService is even instantiated.
 */
@Component
public class PaymentGateway {

    public PaymentGateway() {
        Seq.step("1'", "paymentGateway", "constructed (must exist BEFORE OrderService can be new'd)");
    }

    public String authorize(Order order) {
        return "AUTH-" + order.id();
    }
}
