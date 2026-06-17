package com.example.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsNamedUser() {
        assertEquals("Hello, Ada!", service.greet("Ada"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("Hello, Ada!", service.greet("  Ada  "));
    }

    @Test
    void fallsBackForBlankInput() {
        assertEquals("Hello, stranger!", service.greet("   "));
        assertEquals("Hello, stranger!", service.greet(null));
    }
}
