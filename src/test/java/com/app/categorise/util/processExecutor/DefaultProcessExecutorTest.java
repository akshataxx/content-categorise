package com.app.categorise.util.processExecutor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultProcessExecutorTest {

    private final DefaultProcessExecutor executor = new DefaultProcessExecutor();

    @Nested
    class Run {

        @Test
        void capturesSimpleStdout() throws Exception {
            String output = executor.run(1, "echo", "hello world");
            assertTrue(output.contains("hello world"),
                "Expected output to contain 'hello world', but was: " + output);
        }

        @Test
        void mergesStderrIntoStdout() throws Exception {
            String output = executor.run(1, "sh", "-c", "echo out; echo err >&2");
            assertTrue(output.contains("out"), "Expected output to contain 'out', but was: " + output);
            assertTrue(output.contains("err"), "Expected output to contain 'err', but was: " + output);
        }

        @Test
        void throwsOnNonZeroExitCodeAndIncludesCapturedOutput() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                executor.run(1, "sh", "-c", "echo failure-output; exit 7"));
            assertTrue(ex.getMessage().contains("exit code: 7"),
                "Expected message to contain 'exit code: 7', but was: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("failure-output"),
                "Expected message to contain 'failure-output', but was: " + ex.getMessage());
        }

        @Test
        void capturesMultiLineOutput() throws Exception {
            String output = executor.run(1,
                "sh", "-c", "echo line1; echo line2; echo line3; echo line4; echo line5");
            String[] lines = output.strip().split("\n");
            assertTrue(lines.length >= 5, "Expected at least 5 lines, but got " + lines.length);
            assertTrue(output.contains("line1"));
            assertTrue(output.contains("line5"));
        }
    }
}
