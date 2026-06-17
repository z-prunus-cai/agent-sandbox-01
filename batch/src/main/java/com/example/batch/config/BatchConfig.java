package com.example.batch.config;

import com.example.batch.GreetingTasklet;
import com.example.common.GreetingService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    @Bean
    public GreetingTasklet greetingTasklet() {
        return new GreetingTasklet(new GreetingService());
    }

    @Bean
    public Step greetingStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             GreetingTasklet greetingTasklet) {
        return new StepBuilder("greetingStep", jobRepository)
                .tasklet(greetingTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job greetingJob(JobRepository jobRepository, Step greetingStep) {
        return new JobBuilder("greetingJob", jobRepository)
                .start(greetingStep)
                .build();
    }
}
