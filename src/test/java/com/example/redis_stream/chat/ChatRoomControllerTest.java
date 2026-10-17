package com.example.redis_stream.chat;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatRoomControllerTest {

	private MockMvc mockMvc;
	private ChatRoomService chatRoomService;

	@BeforeEach
	void setUp() {
		chatRoomService = mock(ChatRoomService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new ChatRoomController(chatRoomService)).build();
	}

	@Test
	void returnsOnlyRoomIdWhenCreatingRoom() throws Exception {
		UUID roomId = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");
		when(chatRoomService.createRoom()).thenReturn(roomId);

		mockMvc.perform(post("/api/v1/chat-rooms"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.id", equalTo(roomId.toString())))
			.andExpect(jsonPath("$.created_at").doesNotExist());
	}
}
