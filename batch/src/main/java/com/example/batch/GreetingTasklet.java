package com.example.batch;

import com.example.common.GreetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.Nullable;

/**
 * Minimal batch step that reuses {@link GreetingService} from the common module.
 */
public class GreetingTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(GreetingTasklet.class);

    private final GreetingService greetingService;

    public GreetingTasklet(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @Override
    public RepeatStatus execute(@Nullable StepContribution contribution,
                                @Nullable ChunkContext chunkContext) {
        log.info(greetingService.greet("batch"));
        return RepeatStatus.FINISHED;
    }
}
