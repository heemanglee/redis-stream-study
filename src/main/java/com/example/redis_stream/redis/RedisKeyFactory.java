package com.example.redis_stream.redis;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyFactory {

	public String metadataKey(UUID roomId) {
		return "chat_rooms:" + roomId + ":metadata";
	}

	public String roomIndexKey() {
		return "chat_rooms:index";
	}

	public String messagesKey(UUID roomId) {
		return "chat_rooms:" + roomId + ":messages";
	}

	public String questionEventsKey(UUID roomId) {
		return "chat_rooms:" + roomId + ":question_events";
	}
}
