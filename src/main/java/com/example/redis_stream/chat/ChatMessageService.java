package com.example.redis_stream.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.redis_stream.redis.RedisKeyFactory;

import tools.jackson.databind.ObjectMapper;

@Service
public class ChatMessageService {

	private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyFactory keyFactory;
	private final ChatRoomService chatRoomService;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ChatMessageService(
		StringRedisTemplate redisTemplate,
		RedisKeyFactory keyFactory,
		ChatRoomService chatRoomService,
		ObjectMapper objectMapper,
		Clock clock) {
		this.redisTemplate = redisTemplate;
		this.keyFactory = keyFactory;
		this.chatRoomService = chatRoomService;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public SendMessageResponse send(UUID roomId, String content) {
		if (!chatRoomService.exists(roomId)) {
			throw new ChatRoomNotFoundException(roomId);
		}

		UUID messageId = UUID.randomUUID();
		Instant createdAt = Instant.now(clock);
		ChatMessage message = ChatMessage.create(messageId, content, "user", null, createdAt);
		String messagesKey = keyFactory.messagesKey(roomId);
		redisTemplate.opsForList().rightPush(messagesKey, writeJson(message));
		log.info(
			"event=REDIS_MESSAGE_SAVED redis_command=RPUSH redis_key={} room_id={} message_id={} role=user created_at={}",
			messagesKey, roomId, messageId, createdAt);

		String streamKey = keyFactory.questionEventsKey(roomId);
		MapRecord<String, String, String> record = StreamRecords.newRecord()
			.in(streamKey)
			.ofMap(Map.of(
				"room_id", roomId.toString(),
				"message_id", messageId.toString(),
				"content", content,
				"role", "user"));
		RecordId streamMessageId = redisTemplate.opsForStream().add(record);
		log.info(
			"event=REDIS_EVENT_PUBLISHED redis_command=XADD stream_key={} room_id={} message_id={} role=user stream_message_id={}",
			streamKey, roomId, messageId, streamMessageId.getValue());

		return new SendMessageResponse(messageId, streamMessageId.getValue());
	}

	public List<ChatMessage> findAll(UUID roomId) {
		List<String> values = redisTemplate.opsForList().range(keyFactory.messagesKey(roomId), 0, -1);
		if (values == null) {
			return List.of();
		}
		return values.stream().map(value -> objectMapper.readValue(value, ChatMessage.class)).toList();
	}

	private String writeJson(ChatMessage message) {
		return objectMapper.writeValueAsString(message);
	}
}
