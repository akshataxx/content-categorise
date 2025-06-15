package com.app.categorise.client.whisper;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Profile("dev")
public class MockWhisperClient implements WhisperClient {

    @Override
    public String transcribeAudio(File audioFile) {
        return "This is a mock transcription used in dev mode.";
    }
}
