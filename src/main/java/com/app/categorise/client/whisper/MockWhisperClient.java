package com.app.categorise.client.whisper;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Profile("dev")
public class MockWhisperClient implements WhisperClient {

    @Override
    public String transcribeAudio(File audioFile) {
        System.out.println("Mocking whisper to transcribe audio");
        return "This is 20 days of 20 minute dinners on a budget. Day 14 brings you this crispy, sweet chili chicken, the ultimate fake away at less than $5 per serve and so quick and easy to make at home. To save money, I try to embrace spending time in the kitchen and this means dedicating a little bit of time to a meal plan, making a shopping list and sticking to it, but it also means establishing a cooking routine that suits my lifestyle. I cook large meals on the weekend and then schedule quick speedy meals like this one on weeknights to set myself up for success. The chickens cook to perfection before being coated in an easy to make sweet sticky sauce. Try this one, you will love it.";
    }
}
