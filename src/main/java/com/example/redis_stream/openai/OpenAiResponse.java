package com.example.redis_stream.openai;

import java.util.List;

public record OpenAiResponse(List<Output> output) {

	public record Output(List<Content> content) {
	}

	public record Content(String type, String text) {
	}
}
