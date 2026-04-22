package com.app.categorise.util.processExecutor;

import java.io.IOException;

/**
 * Abstraction over external process execution to enable testing without invoking real commands.
 */
public interface ProcessExecutor {
    void run(int timeoutMinutes, String... command) throws IOException, InterruptedException;

    /**
     * Runs a shell command, captures its combined stdout/stderr output, and returns it as a String.
     *
     * @param timeoutMinutes maximum time to wait for the process to finish
     * @param command        the shell command to run
     * @return the captured stdout/stderr output
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws RuntimeException     if the process times out or exits with a non-zero code
     */
    String runAndCapture(int timeoutMinutes, String... command) throws IOException, InterruptedException;
}
