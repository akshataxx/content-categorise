package com.app.categorise.util.processExecutor;

import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Default implementation that delegates to ProcessRunner.
 */
@Component
public class DefaultProcessExecutor implements ProcessExecutor {
    @Override
    public void run(int timeoutMinutes, String... command) throws IOException, InterruptedException {
        ProcessRunner.runCommand(timeoutMinutes, command);
    }
}
