package com.example.redis_stream.openai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestClientOpenAiClient implements OpenAiClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public RestClientOpenAiClient(RestClient.Builder builder, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public String generate(String content) {
        try {
            OpenAiResponse response = restClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "reasoning", Map.of("effort", properties.reasoning()),
                            "input", content
                            )
                    )
                    .retrieve()
                    .body(OpenAiResponse.class);
            return extractText(response);
        } catch (OpenAiClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OpenAiClientException("OpenAI request failed", exception);
        }
    }

    private String extractText(OpenAiResponse response) {
        List<OpenAiResponse.Output> outputs = response == null ? null : response.output();
        if (outputs == null) {
            throw new OpenAiClientException("OpenAI response did not contain output");
        }
        return outputs.stream()
                .filter(Objects::nonNull)
                .flatMap(output -> output.content() == null ? java.util.stream.Stream.empty() : output.content().stream())
                .filter(Objects::nonNull)
                .map(OpenAiResponse.Content::text)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new OpenAiClientException("OpenAI response did not contain output text"));
    }
}
