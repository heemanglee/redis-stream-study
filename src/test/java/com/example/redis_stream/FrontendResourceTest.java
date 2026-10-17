package com.example.redis_stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FrontendResourceTest {

	@Test
	void exposesConcurrentMessagePageResources() throws IOException {
		String html = readResource("static/index.html");
		String javascript = readResource("static/app.js");
		String stylesheet = readResource("static/styles.css");

		assertThat(html).contains("id=\"message-count\"")
			.contains("id=\"message-inputs\"")
			.contains("id=\"send-button\"")
			.contains("app.js");
		assertThat(javascript).contains("Promise.all")
			.contains("/api/v1/chat-rooms")
			.contains("stream_message_id")
			.contains("question")
			.contains("/question-events/")
			.contains("PENDING")
			.contains("ACKED")
			.contains("상태 조회 재시도 중...")
			.contains("lastError")
			.contains("addEventListener('click'");
		assertThat(stylesheet).contains(".debug-list")
			.contains(".status-success")
			.contains(".status-error");
	}

	private String readResource(String path) throws IOException {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
