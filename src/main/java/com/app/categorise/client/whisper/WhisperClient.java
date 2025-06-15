package com.app.categorise.client.whisper;

import java.io.File;

public interface WhisperClient {
    String transcribeAudio(File audioFile);
}
