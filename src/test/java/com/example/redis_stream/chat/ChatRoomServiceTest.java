package com.example.redis_stream.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.redis_stream.redis.RedisKeyFactory;

class ChatRoomServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-16T12:00:00Z");

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SetOperations<String, String> setOperations;
    private ChatRoomService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        service = new ChatRoomService(
                redisTemplate,
                new RedisKeyFactory(),
                Clock.fixed(CREATED_AT, ZoneOffset.UTC));
    }

    @Test
    void createsRoomMetadataAndRegistersRoomId() {
        var roomId = service.createRoom();

        ArgumentCaptor<String> metadataKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(metadataKey.capture(), metadata.capture());
        verify(setOperations).add("chat_rooms:index", roomId.toString());

        assertThat(metadataKey.getValue()).isEqualTo("chat_rooms:" + roomId + ":metadata");
        assertThat(metadata.getValue())
                .containsEntry("id", roomId.toString())
                .containsEntry("created_at", CREATED_AT.toString());
    }

    @Test
    void checksRoomExistenceThroughMetadataHash() {
        var roomId = java.util.UUID.randomUUID();
        when(hashOperations.hasKey("chat_rooms:" + roomId + ":metadata", "id"))
                .thenReturn(true);

        assertThat(service.exists(roomId)).isTrue();
    }
}
