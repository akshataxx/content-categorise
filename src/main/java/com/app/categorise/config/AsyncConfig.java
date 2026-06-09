package com.app.categorise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private final boolean waitForTasksToCompleteOnShutdown;
    private final int awaitTerminationSeconds;

    public AsyncConfig(
            @Value("${app.media-executor.wait-for-tasks-on-shutdown:true}") boolean waitForTasksToCompleteOnShutdown,
            @Value("${app.media-executor.await-termination-seconds:60}") int awaitTerminationSeconds
    ) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    @Bean(name = "mediaExecutor")
    public ThreadPoolTaskExecutor mediaExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setThreadNamePrefix("media-");
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(20);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        exec.setAwaitTerminationSeconds(awaitTerminationSeconds);
        exec.initialize();
        return exec;
    }
}
