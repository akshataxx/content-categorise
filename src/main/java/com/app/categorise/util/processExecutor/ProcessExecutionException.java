package com.app.categorise.util.processExecutor;

public final class ProcessExecutionException extends RuntimeException {
    private static final int OUTPUT_LIMIT = 4096;

    private final Integer exitCode;
    private final boolean timedOut;
    private final String capturedOutput;

    public ProcessExecutionException(Integer exitCode, boolean timedOut, String capturedOutput) {
        super(message(exitCode, timedOut));
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.capturedOutput = truncate(capturedOutput);
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public String getCapturedOutput() {
        return capturedOutput;
    }

    private static String message(Integer exitCode, boolean timedOut) {
        if (timedOut) {
            return "Command timed out";
        }
        return "Command failed with exit code " + exitCode;
    }

    private static String truncate(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > OUTPUT_LIMIT ? output.substring(0, OUTPUT_LIMIT) : output;
    }
}
