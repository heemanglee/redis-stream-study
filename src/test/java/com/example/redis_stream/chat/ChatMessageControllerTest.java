package com.example.redis_stream.chat;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatMessageControllerTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");

    private MockMvc mockMvc;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = mock(ChatMessageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatMessageController(chatMessageService)).build();
    }

    @Test
    void publishesQuestionAndReturnsIdentifiers() throws Exception {
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(chatMessageService.send(ROOM_ID, "question"))
                .thenReturn(new SendMessageResponse(messageId, "1760616000000-0"));

        mockMvc.perform(post("/api/v1/chat-rooms/{roomId}/messages", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"question\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message_id", equalTo(messageId.toString())))
                .andExpect(jsonPath("$.stream_message_id", equalTo("1760616000000-0")));
    }

    @Test
    void returnsMessagesInStoredOrder() throws Exception {
        when(chatMessageService.findAll(ROOM_ID)).thenReturn(List.of(
                new ChatMessage(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "one", "user", null, java.time.Instant.parse("2026-10-16T12:00:01Z"))));

        mockMvc.perform(get("/api/v1/chat-rooms/{roomId}/messages", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content", equalTo("one")));
    }

    @Test
    void returnsNotFoundWhenRoomDoesNotExist() throws Exception {
        when(chatMessageService.send(ROOM_ID, "question"))
                .thenThrow(new ChatRoomNotFoundException(ROOM_ID));

        mockMvc.perform(post("/api/v1/chat-rooms/{roomId}/messages", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"question\"}"))
                .andExpect(status().isNotFound());
    }
}
