package com.app.categorise.util.processExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * utility class to run yt-dlp and ffmpeg from command line
 * yt-dlp: used for downloading video
 * ffmpeg: used for extracting audio
 */
public class ProcessRunner {
    /**
     * Runs a shell command and waits up to {@code timeoutMinutes} for it to complete.
     * If the process does not finish in time it is forcibly killed and an exception is thrown.
     *
     * @param timeoutMinutes maximum time to wait for the process to finish
     * @param command        the shell command to run
     */
    public static void runCommand(int timeoutMinutes, String... command) throws IOException, InterruptedException {
        // Resolve full path for common tools if they're not absolute paths
        String[] resolvedCommand = new String[command.length];
        for (int i = 0; i < command.length; i++) {
            if (i == 0) { // Only resolve the first element (the executable)
                resolvedCommand[i] = resolveExecutablePath(command[i]);
            } else {
                resolvedCommand[i] = command[i];
            }
        }

        //Build the command
        ProcessBuilder processBuilder = new ProcessBuilder(resolvedCommand);

        // Redirect the output and error streams to the console
        processBuilder.redirectErrorStream(true);

        // Start the command
        Process process = processBuilder.start();

        // Read and print the output from the command
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);  // Print command output to console
            }
        }

        // Wait for the command to finish within the timeout window
        boolean finished = process.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out after " + timeoutMinutes + " minute(s): " + resolvedCommand[0]);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
    
    /**
     * Resolves the full path for common executables that might not be in PATH
     * when running from Java applications
     */
    private static String resolveExecutablePath(String executable) {
        // If it's already an absolute path, return as-is
        if (executable.startsWith("/")) {
            return executable;
        }
        
        // Common paths for Homebrew installations
        String[] commonPaths = {
            "/opt/homebrew/bin/" + executable,  // Apple Silicon Homebrew
            "/usr/local/bin/" + executable,     // Intel Homebrew
            "/usr/bin/" + executable,           // System binaries
            executable                          // Fallback to original
        };
        
        for (String path : commonPaths) {
            if (new java.io.File(path).exists() && new java.io.File(path).canExecute()) {
                return path;
            }
        }
        
        // If none found, return original and let the system try to find it
        return executable;
    }
}
