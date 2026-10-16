package com.example.redis_stream.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RedisKeyFactoryTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");

    private final RedisKeyFactory keyFactory = new RedisKeyFactory();

    @Test
    void createsTheConfiguredRedisKeys() {
        assertThat(keyFactory.metadataKey(ROOM_ID))
                .isEqualTo("chat_rooms:450e8400-e29b-41d4-a716-446655440000:metadata");
        assertThat(keyFactory.roomIndexKey()).isEqualTo("chat_rooms:index");
        assertThat(keyFactory.messagesKey(ROOM_ID))
                .isEqualTo("chat_rooms:450e8400-e29b-41d4-a716-446655440000:messages");
        assertThat(keyFactory.questionEventsKey(ROOM_ID))
                .isEqualTo("chat_rooms:450e8400-e29b-41d4-a716-446655440000:question_events");
    }
}
