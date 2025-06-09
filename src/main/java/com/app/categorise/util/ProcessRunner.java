package com.app.categorise.util;

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
     * Runs a shell command and prints the output
     * @param command the shell command to run
     */
    public static void  runCommand(String... command) throws IOException, InterruptedException {
        //Build the command
        ProcessBuilder processBuilder = new ProcessBuilder(command);

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

        // Wait for the command to finish
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}
