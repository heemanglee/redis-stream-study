package com.example.redis_stream.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientOpenAiClientTest {

	private MockRestServiceServer server;
	private RestClientOpenAiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new RestClientOpenAiClient(
			builder,
			new OpenAiProperties("test-key", "https://api.openai.test", "gpt-5.6-luna", "low"));
	}

	@Test
	void sendsConfiguredModelAndExtractsOutputText() {
		server.expect(requestTo("https://api.openai.test/v1/responses"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(AUTHORIZATION, "Bearer test-key"))
			.andExpect(content().json("""
				{
				  "model": "gpt-5.6-luna",
				  "reasoning": {"effort": "low"},
				  "input": "What is Redis Stream?"
				}
				"""))
			.andRespond(withSuccess("""
				{"output":[{"content":[{"type":"output_text","text":"It is an append-only log."}]}]}
				""", APPLICATION_JSON));

		assertThat(client.generate("What is Redis Stream?")).isEqualTo("It is an append-only log.");
		server.verify();
	}

	@Test
	void raisesTypedExceptionForServerError() {
		server.expect(requestTo("https://api.openai.test/v1/responses"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> client.generate("question"))
			.isInstanceOf(OpenAiClientException.class);
	}

	@Test
	void raisesTypedExceptionForMalformedResponse() {
		server.expect(requestTo("https://api.openai.test/v1/responses"))
			.andRespond(withSuccess("{\"output\":[]}", APPLICATION_JSON));

		assertThatThrownBy(() -> client.generate("question"))
			.isInstanceOf(OpenAiClientException.class);
	}
}
