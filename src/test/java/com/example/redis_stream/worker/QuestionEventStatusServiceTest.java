package com.example.redis_stream.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.redis_stream.chat.ChatMessage;
import com.example.redis_stream.redis.RedisKeyFactory;

import tools.jackson.databind.ObjectMapper;

class QuestionEventStatusServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");
    private static final String STREAM_MESSAGE_ID = "1760616000000-0";
    private static final String GROUP = "llm-workers";

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private ListOperations<String, String> listOperations;
    private QuestionEventStatusService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new QuestionEventStatusService(
                redisTemplate,
                new RedisKeyFactory(),
                new ObjectMapper(),
                new WorkerProperties(GROUP, Duration.ofSeconds(1), "worker-local"));
    }

    @Test
    void reportsPendingWhenEventIsInPel() {
        when(streamOperations.pending(
                eq("chat_rooms:" + ROOM_ID + ":question_events"),
                eq(GROUP),
                any(Range.class),
                eq(1L))).thenReturn(new PendingMessages(GROUP, List.of(
                        new org.springframework.data.redis.connection.stream.PendingMessage(
                                org.springframework.data.redis.connection.stream.RecordId.of(STREAM_MESSAGE_ID),
                                org.springframework.data.redis.connection.stream.Consumer.from(GROUP, "worker-local"),
                                Duration.ofMillis(10), 1))));
        when(listOperations.range("chat_rooms:" + ROOM_ID + ":messages", 0, -1)).thenReturn(List.of());

        QuestionEventStatusResponse response = service.getStatus(ROOM_ID, STREAM_MESSAGE_ID);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.pending()).isTrue();
        assertThat(response.assistantSaved()).isFalse();
        assertThat(response.acknowledged()).isFalse();
    }

    @Test
    void reportsAckedWhenAssistantMessageIsSavedAndEventIsNotPending() throws Exception {
        when(streamOperations.pending(
                eq("chat_rooms:" + ROOM_ID + ":question_events"),
                eq(GROUP),
                any(Range.class),
                eq(1L))).thenReturn(new PendingMessages(GROUP, List.of()));
        ChatMessage assistant = ChatMessage.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "answer",
                "assistant",
                STREAM_MESSAGE_ID,
                Instant.parse("2026-10-16T12:00:03Z"));
        when(listOperations.range("chat_rooms:" + ROOM_ID + ":messages", 0, -1))
                .thenReturn(List.of(new ObjectMapper().writeValueAsString(assistant)));

        QuestionEventStatusResponse response = service.getStatus(ROOM_ID, STREAM_MESSAGE_ID);

        assertThat(response.status()).isEqualTo("ACKED");
        assertThat(response.pending()).isFalse();
        assertThat(response.assistantSaved()).isTrue();
        assertThat(response.acknowledged()).isTrue();
    }

    @Test
    void reportsPublishedBeforeWorkerConsumesEvent() {
        when(streamOperations.pending(
                eq("chat_rooms:" + ROOM_ID + ":question_events"),
                eq(GROUP),
                any(Range.class),
                eq(1L))).thenReturn(new PendingMessages(GROUP, List.of()));
        when(listOperations.range("chat_rooms:" + ROOM_ID + ":messages", 0, -1)).thenReturn(List.of());

        QuestionEventStatusResponse response = service.getStatus(ROOM_ID, STREAM_MESSAGE_ID);

        assertThat(response.status()).isEqualTo("PUBLISHED");
    }
}
