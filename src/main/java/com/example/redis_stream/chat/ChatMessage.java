package com.example.redis_stream.chat;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
	UUID messageId,
	String content,
	String role,
	String streamMessageId,
	Instant createdAt,
	UUID userMessageId) {

	public ChatMessage(UUID messageId, String content, String role, String streamMessageId, Instant createdAt) {
		this(messageId, content, role, streamMessageId, createdAt, null);
	}

	@JsonProperty("message_id")
	public UUID messageId() {
		return messageId;
	}

	@JsonProperty("stream_message_id")
	public String streamMessageId() {
		return streamMessageId;
	}

	@JsonProperty("created_at")
	public Instant createdAt() {
		return createdAt;
	}

	@JsonProperty("user_message_id")
	public UUID userMessageId() {
		return userMessageId;
	}

	public static ChatMessage create(UUID messageId, String content, String role, String streamMessageId,
		Instant createdAt) {
		return new ChatMessage(messageId, content, role, streamMessageId, createdAt, null);
	}

	public static ChatMessage create(
		UUID messageId,
		UUID userMessageId,
		String content,
		String role,
		String streamMessageId,
		Instant createdAt) {
		return new ChatMessage(messageId, content, role, streamMessageId, createdAt, userMessageId);
	}
}
