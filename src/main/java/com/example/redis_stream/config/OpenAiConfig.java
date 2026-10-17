package com.example.redis_stream.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.example.redis_stream.openai.OpenAiProperties;

@Configuration
public class OpenAiConfig {

	@Bean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	OpenAiProperties openAiProperties(
		@Value("${openai.api-key:}")
		String apiKey,
		@Value("${openai.base-url:https://api.openai.com}")
		String baseUrl,
		@Value("${openai.model:gpt-5.6-luna}")
		String model,
		@Value("${openai.reasoning:low}")
		String reasoning) {
		return new OpenAiProperties(apiKey, baseUrl, model, reasoning);
	}
}
