package com.app.categorise.config;

import com.app.categorise.data.client.openai.MockOpenAIClient;
import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.client.openai.OpenAIClientImpl;
import com.app.categorise.data.client.whisper.MockWhisperClient;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.client.whisper.WhisperClientImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIProfileWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.class, RestClient::create)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "openai.api.key=test-key",
                    "openai.api.url=https://api.openai.com"
            )
            .withUserConfiguration(
                    OpenAIClientImpl.class,
                    MockOpenAIClient.class,
                    WhisperClientImpl.class,
                    MockWhisperClient.class
            );

    @Test
    void devProfileUsesMockOpenAIAndWhisperClients() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("app.openai.mode=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context.getBean(OpenAIClient.class)).isInstanceOf(MockOpenAIClient.class);
                    assertThat(context).hasSingleBean(WhisperClient.class);
                    assertThat(context.getBean(WhisperClient.class)).isInstanceOf(MockWhisperClient.class);
                });
    }

    @Test
    void testProfileUsesMockOpenAIAndWhisperClients() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context.getBean(OpenAIClient.class)).isInstanceOf(MockOpenAIClient.class);
                    assertThat(context).hasSingleBean(WhisperClient.class);
                    assertThat(context.getBean(WhisperClient.class)).isInstanceOf(MockWhisperClient.class);
                });
    }

    @Test
    void devProfileCanUseRealOpenAIAndWhisperClients() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("app.openai.mode=real")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context.getBean(OpenAIClient.class)).isInstanceOf(OpenAIClientImpl.class);
                    assertThat(context).hasSingleBean(WhisperClient.class);
                    assertThat(context.getBean(WhisperClient.class)).isInstanceOf(WhisperClientImpl.class);
                });
    }

    @Test
    void prodProfileUsesRealOpenAIAndWhisperClients() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("app.openai.mode=real")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context.getBean(OpenAIClient.class)).isInstanceOf(OpenAIClientImpl.class);
                    assertThat(context).hasSingleBean(WhisperClient.class);
                    assertThat(context.getBean(WhisperClient.class)).isInstanceOf(WhisperClientImpl.class);
                });
    }
}
