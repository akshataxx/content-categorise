package com.app.categorise.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = AsyncConfig.class)
class AsyncConfigTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void mediaExecutorBeanExistsAndRunsOnMediaThreads() throws Exception {
        ThreadPoolTaskExecutor exec = applicationContext.getBean("mediaExecutor", ThreadPoolTaskExecutor.class);
        assertThat(exec).isNotNull();
        assertThat(exec.getThreadNamePrefix()).isEqualTo("media-");
        assertThat(exec.getCorePoolSize()).isEqualTo(2);
        assertThat(exec.getMaxPoolSize()).isEqualTo(4);

        // Submit a simple task and assert it runs on a media-* thread
        String threadName = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), exec)
                .get(3, TimeUnit.SECONDS);
        assertThat(threadName).startsWith("media-");
    }

    @Test
    void mediaExecutorHasCorrectQueueCapacity() {
        ThreadPoolTaskExecutor exec = applicationContext.getBean("mediaExecutor", ThreadPoolTaskExecutor.class);
        assertThat(exec).isNotNull();

        // Verify queue capacity is set correctly
        assertThat(exec.getQueueCapacity()).isEqualTo(20);
    }
}
