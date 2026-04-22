package com.app.categorise.util.processExecutor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerCaptureOutputTest {

    @Nested
    class RunCommandCaptureOutput {

        @Test
        void capturesSimpleStdout() throws Exception {
            String output = ProcessRunner.runCommandCaptureOutput(1, "echo", "hello world");
            assertTrue(output.contains("hello world"),
                    "Expected output to contain 'hello world', but was: " + output);
        }

        @Test
        void mergesStderrIntoStdout() throws Exception {
            String output = ProcessRunner.runCommandCaptureOutput(1, "sh", "-c", "echo out; echo err >&2");
            assertTrue(output.contains("out"), "Expected output to contain 'out', but was: " + output);
            assertTrue(output.contains("err"), "Expected output to contain 'err', but was: " + output);
        }

        @Test
        void throwsOnNonZeroExitCode() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    ProcessRunner.runCommandCaptureOutput(1, "sh", "-c", "echo failure-output; exit 7"));
            assertTrue(ex.getMessage().contains("exit code: 7"),
                    "Expected message to contain 'exit code: 7', but was: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("failure-output"),
                    "Expected message to contain 'failure-output', but was: " + ex.getMessage());
        }

        @Test
        void capturesMultiLineOutput() throws Exception {
            String output = ProcessRunner.runCommandCaptureOutput(1,
                    "sh", "-c", "echo line1; echo line2; echo line3; echo line4; echo line5");
            assertTrue(output.contains("line1"), "Missing line1");
            assertTrue(output.contains("line2"), "Missing line2");
            assertTrue(output.contains("line3"), "Missing line3");
            assertTrue(output.contains("line4"), "Missing line4");
            assertTrue(output.contains("line5"), "Missing line5");

            // Verify all 5 lines are present as separate lines
            String[] lines = output.strip().split("\n");
            assertTrue(lines.length >= 5,
                    "Expected at least 5 lines, but got " + lines.length);
        }
    }
}
