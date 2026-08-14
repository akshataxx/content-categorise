package com.app.categorise.util.processExecutor;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Default implementation that runs commands via {@link ProcessBuilder}.
 * Resolves common Homebrew/system locations for unqualified executables so that
 * Java processes launched outside a shell (where PATH may be stripped) can find
 * tools like {@code yt-dlp} and {@code ffmpeg}.
 */
@Component
public class DefaultProcessExecutor implements ProcessExecutor {

    private static final int CAPTURED_OUTPUT_LIMIT = 4096;
    private static final int SUCCESS_OUTPUT_LIMIT = 2 * 1024 * 1024;

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

        StringBuilder output = new StringBuilder();
        StringBuilder captured = new StringBuilder();
        AtomicBoolean outputExceeded = new AtomicBoolean();
        AtomicReference<IOException> outputFailure = new AtomicReference<>();
        Thread outputReader = new Thread(() -> readOutput(process, output, captured, outputExceeded, outputFailure), "process-output-reader");
        outputReader.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            List<ProcessHandle> descendants = terminateProcessTree(process);
            awaitProcessTree(process, descendants);
            joinOutputReader(outputReader);
            Thread.currentThread().interrupt();
            throw e;
        }
        if (!finished) {
            List<ProcessHandle> descendants = terminateProcessTree(process);
            awaitProcessTree(process, descendants);
            joinOutputReader(outputReader);
            throw new ProcessExecutionException(null, true, captured.toString());
        }
        joinOutputReader(outputReader);
        if (outputFailure.get() != null) {
            throw outputFailure.get();
        }

        int exitCode = process.exitValue();
        if (exitCode != 0 || outputExceeded.get()) {
            throw new ProcessExecutionException(exitCode, false, captured.toString());
        }
        return output.toString();
    }

    private static void readOutput(
        Process process,
        StringBuilder output,
        StringBuilder captured,
        AtomicBoolean outputExceeded,
        AtomicReference<IOException> outputFailure
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                appendSuccessfulOutput(output, buffer, read, outputExceeded);
                appendBounded(captured, buffer, read);
            }
        } catch (IOException e) {
            outputFailure.set(e);
        }
    }

    private static void joinOutputReader(Thread outputReader) throws InterruptedException {
        outputReader.join();
    }

    private static void appendBounded(StringBuilder captured, char[] buffer, int length) {
        int remaining = CAPTURED_OUTPUT_LIMIT - captured.length();
        if (remaining <= 0) {
            return;
        }
        captured.append(buffer, 0, Math.min(remaining, length));
    }

    private static void appendSuccessfulOutput(StringBuilder output, char[] buffer, int length, AtomicBoolean outputExceeded) {
        int remaining = SUCCESS_OUTPUT_LIMIT - output.length();
        if (remaining <= 0) {
            outputExceeded.set(true);
            return;
        }
        int charactersToAppend = Math.min(remaining, length);
        output.append(buffer, 0, charactersToAppend);
        if (charactersToAppend < length) {
            outputExceeded.set(true);
        }
    }

    static List<ProcessHandle> terminateProcessTree(
        Process process,
        Function<ProcessHandle, Stream<ProcessHandle>> descendantsProvider
    ) {
        List<ProcessHandle> descendants;
        try {
            descendants = descendantsProvider.apply(process.toHandle()).toList();
        } catch (RuntimeException ignored) {
            descendants = List.of();
        }
        for (ProcessHandle descendant : descendants) {
            try {
                descendant.destroyForcibly();
            } catch (RuntimeException ignored) {
            }
        }
        process.destroyForcibly();
        return descendants;
    }

    private static List<ProcessHandle> terminateProcessTree(Process process) {
        return terminateProcessTree(process, ProcessHandle::descendants);
    }

    private static void awaitProcessTree(Process process, List<ProcessHandle> descendants) throws InterruptedException {
        process.waitFor();
        for (ProcessHandle descendant : descendants) {
            try {
                descendant.onExit().get();
            } catch (ExecutionException | RuntimeException ignored) {
            }
        }
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
