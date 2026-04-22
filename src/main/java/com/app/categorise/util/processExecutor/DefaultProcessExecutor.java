package com.app.categorise.util.processExecutor;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation that runs commands via {@link ProcessBuilder}.
 * Resolves common Homebrew/system locations for unqualified executables so that
 * Java processes launched outside a shell (where PATH may be stripped) can find
 * tools like {@code yt-dlp} and {@code ffmpeg}.
 */
@Component
public class DefaultProcessExecutor implements ProcessExecutor {

    /** Truncate the captured output included in failure messages to this many bytes. */
    private static final int FAILURE_OUTPUT_LIMIT = 4096;

    /** Common locations checked for unqualified executables (macOS-friendly). */
    private static final String[] COMMON_BIN_DIRS = {
        "/opt/homebrew/bin/", // Apple Silicon Homebrew
        "/usr/local/bin/",    // Intel Homebrew
        "/usr/bin/"           // System binaries
    };

    @Override
    public String run(int timeoutMinutes, String... command) throws IOException, InterruptedException {
        String[] resolved = command.clone();
        resolved[0] = resolveExecutablePath(command[0]);

        Process process = new ProcessBuilder(resolved)
            .redirectErrorStream(true)
            .start();

        StringBuilder captured = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                captured.append(line).append('\n');
            }
        }

        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out after " + timeoutMinutes + " minute(s): " + resolved[0]);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String tail = captured.length() > FAILURE_OUTPUT_LIMIT
                ? captured.substring(0, FAILURE_OUTPUT_LIMIT)
                : captured.toString();
            throw new RuntimeException("Command failed with exit code: " + exitCode + ". Output: " + tail);
        }

        return captured.toString();
    }

    /**
     * If {@code executable} is an absolute path, returns it unchanged.
     * Otherwise checks common Homebrew/system bin dirs and returns the first
     * existing executable match. Falls back to the original (letting the OS
     * resolve via PATH) if none is found.
     */
    private static String resolveExecutablePath(String executable) {
        if (executable.startsWith("/")) {
            return executable;
        }
        for (String dir : COMMON_BIN_DIRS) {
            File candidate = new File(dir + executable);
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return executable;
    }
}
