package com.example.redis_stream.chat;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendMessageResponse(UUID messageId, String streamMessageId) {

	@JsonProperty("message_id")
	public UUID messageId() {
		return messageId;
	}

	@JsonProperty("stream_message_id")
	public String streamMessageId() {
		return streamMessageId;
	}
}
