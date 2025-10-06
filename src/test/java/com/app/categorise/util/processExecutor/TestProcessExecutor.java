package com.app.categorise.util.processExecutor;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double that does not execute real OS commands.
 */
@Component
@Primary
public class TestProcessExecutor implements ProcessExecutor {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public void run(String... command) throws IOException, InterruptedException {
        calls.incrementAndGet();
        // no-op
    }

    public int calls() { return calls.get(); }
}
