package com.app.categorise.domain.service;

import com.app.categorise.util.processExecutor.ProcessExecutionException;
import org.springframework.stereotype.Component;

@Component
public class TikTokChallengeDetector {
    public boolean isChallenge(ProcessExecutionException exception) {
        if (exception.isTimedOut() || exception.getExitCode() == null || exception.getExitCode() == 0) {
            return false;
        }
        String output = exception.getCapturedOutput();
        if (!output.contains("[TikTok]")) {
            return false;
        }
        return output.contains("Unexpected response from webpage request")
            || output.contains("Unable to extract challenge data")
            || output.contains("Unable to solve JS challenge")
            || output.contains("Unable to extract universal data for rehydration");
    }
}
