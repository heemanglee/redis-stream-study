package com.example.redis_stream.chat;

import java.util.UUID;

public record QuestionEvent(UUID roomId, UUID messageId, String content, String role) {
}
