package com.example.backend;

import com.example.common.GreetingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController() {
        this.greetingService = new GreetingService();
    }

    @GetMapping("/api/greeting")
    public Map<String, String> greeting(@RequestParam(required = false) String name) {
        return Map.of("message", greetingService.greet(name));
    }
}
