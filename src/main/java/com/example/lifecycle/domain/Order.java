package com.example.lifecycle.domain;

/** Trivial value object passed to {@code OrderService.placeOrder}. */
public record Order(String id, long amountCents) {
}
