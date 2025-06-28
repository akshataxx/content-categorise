package com.app.categorise.data.client.whisper;

import java.io.File;

public interface WhisperClient {
    String transcribeAudio(File audioFile);
}
