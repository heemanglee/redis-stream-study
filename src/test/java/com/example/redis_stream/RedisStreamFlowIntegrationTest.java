package com.example.redis_stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.redis_stream.chat.ChatMessage;
import com.example.redis_stream.chat.ChatMessageService;
import com.example.redis_stream.chat.ChatRoomService;
import com.example.redis_stream.chat.SendMessageResponse;
import com.example.redis_stream.openai.OpenAiClient;
import com.example.redis_stream.redis.RedisKeyFactory;
import com.example.redis_stream.worker.ConsumerGroupManager;
import com.example.redis_stream.worker.QuestionEventProcessor;
import com.example.redis_stream.worker.WorkerProperties;

import tools.jackson.databind.ObjectMapper;

class RedisStreamFlowIntegrationTest {

	@Test
	void storesQuestionConsumesItStoresAnswerAndAcknowledgesIt() throws Exception {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
		connectionFactory.afterPropertiesSet();
		StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();

		RedisKeyFactory keys = new RedisKeyFactory();
		Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
		UUID roomId = null;
		String metadataKey = null;
		String messagesKey = null;
		String streamKey = null;
		String group = "integration-" + UUID.randomUUID();

		try {
			Assumptions.assumeTrue(
				Boolean.TRUE.equals(redis.getConnectionFactory().getConnection().ping().equals("PONG")),
				"local Redis is unavailable");
			ChatRoomService rooms = new ChatRoomService(redis, keys, clock);
			roomId = rooms.createRoom();
			metadataKey = keys.metadataKey(roomId);
			messagesKey = keys.messagesKey(roomId);
			streamKey = keys.questionEventsKey(roomId);

			ChatMessageService messages = new ChatMessageService(redis, keys, rooms, new ObjectMapper(), clock);
			SendMessageResponse sent = messages.send(roomId, "question");
			ConsumerGroupManager groups = new ConsumerGroupManager(redis,
				new WorkerProperties(group, Duration.ofSeconds(1), "integration-worker"));
			groups.ensureGroup(streamKey);

			List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
				Consumer.from(group, "integration-worker"),
				StreamReadOptions.empty().count(1),
				StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
			assertThat(records).hasSize(1);

			OpenAiClient fakeClient = ignored -> "answer";
			QuestionEventProcessor processor = new QuestionEventProcessor(
				redis, keys, fakeClient, new ObjectMapper(), clock,
				new WorkerProperties(group, Duration.ofSeconds(1), "integration-worker"));
			MapRecord<String, Object, Object> record = records.getFirst();
			processor.process(
				new com.example.redis_stream.chat.QuestionEvent(
					roomId, sent.messageId(), "question", "user"),
				record.getId().getValue());

			List<ChatMessage> stored = messages.findAll(roomId);
			assertThat(stored).extracting(ChatMessage::role).containsExactly("user", "assistant");
			assertThat(stored.get(1).streamMessageId()).isEqualTo(record.getId().getValue());
			assertThat(redis.opsForStream().pending(streamKey, group).getTotalPendingMessages()).isZero();
		} finally {
			if (roomId != null) {
				redis.delete(List.of(metadataKey, messagesKey, streamKey));
				redis.opsForSet().remove(keys.roomIndexKey(), roomId.toString());
			}
			connectionFactory.destroy();
		}
	}
}
