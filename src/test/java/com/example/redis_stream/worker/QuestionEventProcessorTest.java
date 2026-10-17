package com.example.redis_stream.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.redis_stream.chat.ChatMessage;
import com.example.redis_stream.chat.QuestionEvent;
import com.example.redis_stream.openai.OpenAiClient;
import com.example.redis_stream.redis.RedisKeyFactory;

import tools.jackson.databind.ObjectMapper;

class QuestionEventProcessorTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");
    private static final UUID USER_MESSAGE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant CREATED_AT = Instant.parse("2026-10-16T12:00:03Z");
    private static final String STREAM_MESSAGE_ID = "1760616000000-0";
    private static final String GROUP = "llm-workers";

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOperations;
    private StreamOperations<String, Object, Object> streamOperations;
    private OpenAiClient openAiClient;
    private QuestionEventProcessor processor;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOperations = mock(ListOperations.class);
        streamOperations = mock(StreamOperations.class);
        openAiClient = mock(OpenAiClient.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(openAiClient.generate("question")).thenReturn("answer");
        processor = new QuestionEventProcessor(
                redisTemplate,
                new RedisKeyFactory(),
                openAiClient,
                new ObjectMapper(),
                Clock.fixed(CREATED_AT, ZoneOffset.UTC),
                new WorkerProperties(GROUP, Duration.ofSeconds(1), "worker-test"));
    }

    @Test
    void storesAssistantMessageBeforeAcknowledgingEvent() throws Exception {
        processor.process(new QuestionEvent(ROOM_ID, USER_MESSAGE_ID, "question", "user"), STREAM_MESSAGE_ID);

        InOrder order = inOrder(listOperations, streamOperations);
        order.verify(listOperations).rightPush(eq("chat_rooms:" + ROOM_ID + ":messages"), anyString());
        order.verify(streamOperations).acknowledge(
                "chat_rooms:" + ROOM_ID + ":question_events", GROUP, STREAM_MESSAGE_ID);

        ArgumentCaptor<String> assistantJson = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(eq("chat_rooms:" + ROOM_ID + ":messages"), assistantJson.capture());
        ChatMessage assistant = new ObjectMapper().readValue(assistantJson.getValue(), ChatMessage.class);
        assertThat(assistant.role()).isEqualTo("assistant");
        assertThat(assistant.userMessageId()).isEqualTo(USER_MESSAGE_ID);
        assertThat(assistant.content()).isEqualTo("answer");
        assertThat(assistant.streamMessageId()).isEqualTo(STREAM_MESSAGE_ID);
        assertThat(assistant.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void doesNotAcknowledgeWhenLlmFails() {
        when(openAiClient.generate("question")).thenThrow(new RuntimeException("OpenAI unavailable"));

        assertThatThrownBy(() -> processor.process(
                new QuestionEvent(ROOM_ID, USER_MESSAGE_ID, "question", "user"), STREAM_MESSAGE_ID))
                .isInstanceOf(RuntimeException.class);

        verify(listOperations, never()).rightPush(anyString(), anyString());
        verify(streamOperations, never()).acknowledge(anyString(), anyString(), anyString());
    }
}
