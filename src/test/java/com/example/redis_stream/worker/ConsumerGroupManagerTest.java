package com.example.redis_stream.worker;

import java.time.Duration;

import io.lettuce.core.RedisBusyException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ConsumerGroupManagerTest {

	private static final String STREAM_KEY = "chat_rooms:450e8400-e29b-41d4-a716-446655440000:question_events";
	private static final String GROUP = "llm-workers";

	private StreamOperations<String, Object, Object> streamOperations;
	private ConsumerGroupManager manager;

	@BeforeEach
	void setUp() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		streamOperations = mock(StreamOperations.class);
		when(redisTemplate.opsForStream()).thenReturn(streamOperations);
		manager = new ConsumerGroupManager(redisTemplate,
			new WorkerProperties(GROUP, Duration.ofSeconds(1), "worker-test"));
	}

	@Test
	void createsGroupFromBeginning() {
		manager.ensureGroup(STREAM_KEY);

		verify(streamOperations).createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP);
	}

	@Test
	void treatsBusyGroupAsAlreadyCreated() {
		doThrow(new RedisSystemException("BUSYGROUP Consumer Group name already exists", null))
			.when(streamOperations).createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP);

		manager.ensureGroup(STREAM_KEY);
	}

	@Test
	void treatsBusyGroupWrappedByLettuceAsAlreadyCreated() {
		RedisBusyException busyGroup = new RedisBusyException("BUSYGROUP Consumer Group name already exists");
		doThrow(new RedisSystemException("Error in execution", busyGroup))
			.when(streamOperations).createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP);

		manager.ensureGroup(STREAM_KEY);
	}
}
