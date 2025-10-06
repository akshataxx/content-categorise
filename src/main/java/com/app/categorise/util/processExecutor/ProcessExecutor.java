package com.app.categorise.util.processExecutor;

import java.io.IOException;

/**
 * Abstraction over external process execution to enable testing without invoking real commands.
 */
public interface ProcessExecutor {
    void run(String... command) throws IOException, InterruptedException;
}
