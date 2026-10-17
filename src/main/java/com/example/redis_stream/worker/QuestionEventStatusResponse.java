package com.example.redis_stream.worker;

public record QuestionEventStatusResponse(
	String streamMessageId,
	String status,
	boolean pending,
	boolean assistantSaved,
	boolean acknowledged) {
}
