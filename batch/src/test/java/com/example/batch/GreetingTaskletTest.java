package com.example.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.common.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

class GreetingTaskletTest {

    @Test
    void executesAndFinishes() {
        GreetingTasklet tasklet = new GreetingTasklet(new GreetingService());
        assertEquals(RepeatStatus.FINISHED, tasklet.execute(null, null));
    }
}
