package com.example.redis_stream.chat;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ChatRoomNotFoundException extends RuntimeException {

	public ChatRoomNotFoundException(UUID roomId) {
		super("Chat room not found: " + roomId);
	}
}
