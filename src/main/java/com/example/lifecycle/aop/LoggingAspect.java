package com.example.lifecycle.aop;

import com.example.lifecycle.support.Seq;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * A trivial around-aspect. Its existence is what makes the auto-proxy creator
 * decide {@code OrderService} needs a proxy, which is exactly what fires step 10
 * (and step 3, if OrderService were in a cycle). It also lets us PROVE the
 * self-invocation trap: advice fires around {@code placeOrder} (called through the
 * proxy) but NOT around {@code audit} (reached via {@code this.audit()}).
 */
@Aspect
@Component
public class LoggingAspect {

    @Around("execution(* com.example.lifecycle.domain.OrderService.placeOrder(..))")
    public Object aroundPlaceOrder(ProceedingJoinPoint pjp) throws Throwable {
        Seq.note("aspect", ">> advice BEFORE placeOrder (we went through the proxy)");
        Object result = pjp.proceed();
        Seq.note("aspect", "<< advice AFTER placeOrder");
        return result;
    }

    @Around("execution(* com.example.lifecycle.domain.OrderService.audit(..))")
    public Object aroundAudit(ProceedingJoinPoint pjp) throws Throwable {
        Seq.note("aspect", ">> advice around audit -- SHOULD NOT PRINT for self-invocation");
        return pjp.proceed();
    }
}
