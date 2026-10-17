package com.example.redis_stream.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.redis_stream.redis.RedisKeyFactory;

@Service
public class ChatRoomService {

	private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyFactory keyFactory;
	private final Clock clock;

	public ChatRoomService(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory, Clock clock) {
		this.redisTemplate = redisTemplate;
		this.keyFactory = keyFactory;
		this.clock = clock;
	}

	public UUID createRoom() {
		UUID roomId = UUID.randomUUID();
		Instant createdAt = Instant.now(clock);
		String metadataKey = keyFactory.metadataKey(roomId);

		redisTemplate.opsForHash().putAll(metadataKey,
			Map.of(
				"id", roomId.toString(),
				"created_at", createdAt.toString()));
		redisTemplate.opsForSet().add(keyFactory.roomIndexKey(), roomId.toString());

		log.info("event=CHAT_ROOM_CREATED redis_command=HSET redis_key={} room_id={} created_at={}",
			metadataKey, roomId, createdAt);
		log.info("event=CHAT_ROOM_INDEXED redis_command=SADD redis_key={} room_id={}",
			keyFactory.roomIndexKey(), roomId);

		return roomId;
	}

	public boolean exists(UUID roomId) {
		return Boolean.TRUE.equals(redisTemplate.opsForHash()
			.hasKey(keyFactory.metadataKey(roomId), "id"));
	}
}
