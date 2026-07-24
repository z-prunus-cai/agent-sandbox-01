package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The whole lifecycle, embodied in one bean.
 *
 * <p>Compared to note 003's sketch, this version actually implements every
 * callback interface the sketch only mentioned, so each numbered step in Table A
 * prints real evidence of WHEN it runs and WHAT the object looks like at that
 * instant. Read the console top-to-bottom and you are reading the object being
 * assembled field by field.
 *
 * <p>Registered via {@code @Bean} in {@code DemoConfig} (not component-scanned)
 * so we can attach a custom {@code initMethod}/{@code destroyMethod} — the only
 * way to exercise steps 9 and D3.
 */
public class OrderService
        implements BeanNameAware, BeanClassLoaderAware, BeanFactoryAware,
        ApplicationContextAware, EnvironmentAware,
        InitializingBean, DisposableBean, SmartLifecycle {

    // --- constructor-injected: has a value from the very first instant (step 1') ---
    private final PaymentGateway gateway;

    // --- field-injected: null until step 5 ---
    @Autowired
    private InventoryClient inventory;

    // --- value-injected: 0 until step 5, then resolved from application.properties ---
    @Value("${order.timeout:30}")
    private int timeout;

    // --- set by Aware callbacks (step 6 / 7a) ---
    private String myName;
    private ClassLoader classLoader;
    private BeanFactory beanFactory;
    private ApplicationContext applicationContext;
    private Environment environment;

    // --- built in @PostConstruct (step 7b), torn down in @PreDestroy (D1) ---
    private ExecutorService pool;

    private volatile boolean running = false;

    public OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
        Seq.step("1'", "orderService", "constructor ran -> " + snapshot());
    }

    /** Compact view of every field's current state; printed at each callback. */
    private String snapshot() {
        return String.format(
                "gateway=%s inventory=%s timeout=%d name=%s ctx=%s pool=%s",
                gateway != null ? "SET" : "null",
                inventory != null ? "SET" : "null",
                timeout,
                myName != null ? myName : "null",
                applicationContext != null ? "SET" : "null",
                pool != null ? "SET" : "null");
    }

    // ===================== step 6: invokeAwareMethods (hard-coded order) =====================

    @Override
    public void setBeanName(String name) {
        this.myName = name;
        Seq.step("6", "orderService", "BeanNameAware.setBeanName(\"" + name + "\") -> " + snapshot());
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        Seq.step("6", "orderService", "BeanClassLoaderAware.setBeanClassLoader(...)");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        Seq.step("6", "orderService", "BeanFactoryAware.setBeanFactory(...)");
    }

    // ===================== step 7a: context-layer Aware (via ApplicationContextAwareProcessor) =====================

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        Seq.step("7a", "orderService", "EnvironmentAware.setEnvironment(...) [context-layer Aware]");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        Seq.step("7a", "orderService", "ApplicationContextAware.setApplicationContext(...) -> " + snapshot());
    }

    // ===================== step 7b: @PostConstruct =====================

    @PostConstruct
    public void init() {
        this.pool = Executors.newFixedThreadPool(4);
        Seq.step("7b", "orderService", "@PostConstruct init(): built thread pool -> " + snapshot());
        Seq.note("orderService", "   inside @PostConstruct 'this' is the RAW object: " + getClass().getName());
    }

    // ===================== step 8: InitializingBean =====================

    @Override
    public void afterPropertiesSet() {
        Seq.step("8", "orderService", "InitializingBean.afterPropertiesSet() [init method (1)]");
    }

    // ===================== step 9: custom init-method (wired in DemoConfig) =====================

    public void customInit() {
        Seq.step("9", "orderService", "custom init-method customInit() [init method (2)]");
    }

    // ===================== business method (advised by LoggingAspect) =====================

    public String placeOrder(Order order) {
        Seq.note("orderService", "placeOrder() body running on " + getClass().getSimpleName()
                + " (proxy? " + getClass().getName().contains("$$SpringCGLIB$$") + ")");
        gateway.authorize(order);
        inventory.reserve(order);
        // Self-invocation: calling this.audit() goes through the RAW target, NOT the
        // proxy, so the aspect does NOT fire around it. Classic "annotation not working" trap.
        this.audit(order);
        return "OK-" + order.id();
    }

    public void audit(Order order) {
        Seq.note("orderService", "audit() body running (reached via this.audit() -> aspect is bypassed)");
    }

    // ===================== step 13: SmartLifecycle =====================

    @Override
    public void start() {
        this.running = true;
        Seq.step("13", "orderService", "SmartLifecycle.start() [container finishRefresh -> now serving]");
    }

    @Override
    public void stop() {
        this.running = false;
        Seq.step("13", "orderService", "SmartLifecycle.stop() [container closing, BEFORE destruction callbacks]");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // ===================== D1: @PreDestroy =====================

    @PreDestroy
    public void cleanup() {
        if (pool != null) {
            pool.shutdown();
        }
        Seq.step("D1", "orderService", "@PreDestroy cleanup(): thread pool shut down");
    }

    // ===================== D2: DisposableBean =====================

    @Override
    public void destroy() {
        Seq.step("D2", "orderService", "DisposableBean.destroy() [destruction (1)]");
    }

    // ===================== D3: custom destroy-method (wired in DemoConfig) =====================

    public void customDestroy() {
        Seq.step("D3", "orderService", "custom destroy-method customDestroy() [destruction (2)]");
    }
}
