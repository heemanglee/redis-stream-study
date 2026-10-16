package com.example.redis_stream.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatRoomMetadata(UUID id, Instant createdAt) {
}
