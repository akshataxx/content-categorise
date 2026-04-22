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
    private final AtomicInteger captureCalls = new AtomicInteger();
    private volatile String captureOutput = "";
    private volatile RuntimeException captureException = null;
    private volatile String[] lastCaptureCommand = new String[0];

    @Override
    public void run(int timeoutMinutes, String... command) throws IOException, InterruptedException {
        calls.incrementAndGet();
        // no-op
    }

    @Override
    public String runAndCapture(int timeoutMinutes, String... command) throws IOException, InterruptedException {
        captureCalls.incrementAndGet();
        lastCaptureCommand = command;
        if (captureException != null) {
            throw captureException;
        }
        return captureOutput;
    }

    public int calls() { return calls.get(); }

    public int captureCalls() { return captureCalls.get(); }

    public String[] lastCaptureCommand() { return lastCaptureCommand; }

    public void setCaptureOutput(String output) { this.captureOutput = output; }

    public void setCaptureException(RuntimeException ex) { this.captureException = ex; }
}
