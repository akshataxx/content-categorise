package com.app.categorise.util.processExecutor;

import java.io.IOException;

/**
 * Abstraction over external process execution to enable testing without invoking real commands.
 * Always captures combined stdout/stderr; callers that don't need the output can ignore the return value.
 */
public interface ProcessExecutor {
    /**
     * Runs a shell command, captures its combined stdout/stderr output, and returns it as a String.
     * Forcibly kills the process and throws if the timeout is exceeded.
     * Throws if the process exits with a non-zero code (the captured output is included in the exception message).
     *
     * @param timeoutMinutes maximum time to wait for the process to finish
     * @param command        the command and its arguments
     * @return the captured stdout/stderr output
     */
    String run(int timeoutMinutes, String... command) throws IOException, InterruptedException;
}
