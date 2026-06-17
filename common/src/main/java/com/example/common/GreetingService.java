package com.example.common;

/**
 * Reusable business logic shared by the backend (web) and batch modules.
 */
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, stranger!";
        }
        return "Hello, " + name.trim() + "!";
    }
}
