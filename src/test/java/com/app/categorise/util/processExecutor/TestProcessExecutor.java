package com.app.categorise.util.processExecutor;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double that does not execute real OS commands.
 *
 * <p>Records every call and exposes hooks to stub the returned output or to
 * throw an exception, so individual tests can drive both the success and
 * failure paths through {@link ProcessExecutor#run}.
 */
@Component
@Primary
public class TestProcessExecutor implements ProcessExecutor {
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String output = "";
    private volatile RuntimeException exception = null;
    private volatile String[] lastCommand = new String[0];

    @Override
    public String run(int timeoutMinutes, String... command) {
        calls.incrementAndGet();
        lastCommand = command;
        if (exception != null) {
            throw exception;
        }
        return output;
    }

    public int calls() { return calls.get(); }

    public String[] lastCommand() { return lastCommand; }

    public void setOutput(String output) { this.output = output; }

    public void setException(RuntimeException ex) { this.exception = ex; }
}
