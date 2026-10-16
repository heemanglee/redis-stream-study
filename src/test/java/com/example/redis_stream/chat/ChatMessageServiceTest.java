package com.example.redis_stream.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;
import com.example.redis_stream.redis.RedisKeyFactory;

class ChatMessageServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");
    private static final Instant CREATED_AT = Instant.parse("2026-10-16T12:00:01Z");

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOperations;
    private StreamOperations<String, Object, Object> streamOperations;
    private ChatRoomService chatRoomService;
    private ChatMessageService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOperations = mock(ListOperations.class);
        streamOperations = mock(StreamOperations.class);
        chatRoomService = mock(ChatRoomService.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(chatRoomService.exists(ROOM_ID)).thenReturn(true);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("1760616000000-0"));
        service = new ChatMessageService(
                redisTemplate,
                new RedisKeyFactory(),
                chatRoomService,
                new ObjectMapper(),
                Clock.fixed(CREATED_AT, ZoneOffset.UTC));
    }

    @Test
    void savesUserMessageBeforePublishingQuestionEvent() throws Exception {
        SendMessageResponse response = service.send(ROOM_ID, "Redis Stream이 뭐야?");

        InOrder order = inOrder(listOperations, streamOperations);
        order.verify(listOperations).rightPush(
                org.mockito.ArgumentMatchers.eq("chat_rooms:" + ROOM_ID + ":messages"),
                org.mockito.ArgumentMatchers.anyString());
        order.verify(streamOperations).add(any(MapRecord.class));

        ArgumentCaptor<String> messageJson = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(
                org.mockito.ArgumentMatchers.eq("chat_rooms:" + ROOM_ID + ":messages"),
                messageJson.capture());
        assertThat(messageJson.getValue())
                .contains("\"message_id\"")
                .contains("\"created_at\"")
                .doesNotContain("stream_message_id");
        ChatMessage message = new ObjectMapper().readValue(messageJson.getValue(), ChatMessage.class);
        assertThat(message.messageId()).isEqualTo(response.messageId());
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.streamMessageId()).isEqualTo("1760616000000-0");
    }

    @Test
    void rejectsMessageForMissingRoomBeforeWritingRedis() {
        when(chatRoomService.exists(ROOM_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.send(ROOM_ID, "question"))
                .isInstanceOf(ChatRoomNotFoundException.class);

        verify(listOperations, never()).rightPush(any(String.class), any(String.class));
        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    void readsMessagesInListOrder() throws Exception {
        when(listOperations.range("chat_rooms:" + ROOM_ID + ":messages", 0, -1))
                .thenReturn(List.of(
                        "{\"messageId\":\"550e8400-e29b-41d4-a716-446655440000\",\"content\":\"one\",\"role\":\"user\",\"createdAt\":\"2026-10-16T12:00:01Z\"}",
                        "{\"messageId\":\"650e8400-e29b-41d4-a716-446655440000\",\"content\":\"two\",\"role\":\"assistant\",\"createdAt\":\"2026-10-16T12:00:03Z\"}"));

        List<ChatMessage> messages = service.findAll(ROOM_ID);

        assertThat(messages).extracting(ChatMessage::content).containsExactly("one", "two");
    }
}
