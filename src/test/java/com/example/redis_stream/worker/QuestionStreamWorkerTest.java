package com.example.redis_stream.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.redis_stream.redis.RedisKeyFactory;

class QuestionStreamWorkerTest {

    private static final UUID ROOM_ID = UUID.fromString("450e8400-e29b-41d4-a716-446655440000");
    private static final String STREAM_KEY = "chat_rooms:" + ROOM_ID + ":question_events";

    private StringRedisTemplate redisTemplate;
    private SetOperations<String, String> setOperations;
    private StreamOperations<String, Object, Object> streamOperations;
    private ConsumerGroupManager consumerGroupManager;
    private QuestionEventProcessor processor;
    private ExecutorService executor;
    private QuestionStreamWorker worker;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        streamOperations = mock(StreamOperations.class);
        consumerGroupManager = mock(ConsumerGroupManager.class);
        processor = mock(QuestionEventProcessor.class);
        executor = Executors.newFixedThreadPool(5);

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(setOperations.members("chat_rooms:index")).thenReturn(Set.of(ROOM_ID.toString()));
        when(redisTemplate.hasKey(STREAM_KEY)).thenReturn(true);
        doReturn(records(5)).when(streamOperations).read(
                any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));

        worker = new QuestionStreamWorker(
                redisTemplate,
                new RedisKeyFactory(),
                consumerGroupManager,
                processor,
                new WorkerProperties("llm-workers", Duration.ofSeconds(1), "worker-local"),
                executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void processesFiveRecordsConcurrently() throws Exception {
        CountDownLatch started = new CountDownLatch(5);
        doAnswer(invocation -> {
            started.countDown();
            return null;
        }).when(processor).process(any(), any());

        worker.poll();

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<StreamReadOptions> options = ArgumentCaptor.forClass(StreamReadOptions.class);
        verify(streamOperations).read(any(Consumer.class), options.capture(), any(StreamOffset.class));
        assertThat(options.getValue().getCount()).isEqualTo(5L);
    }

    private List<MapRecord<String, Object, Object>> records(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    MapRecord<String, Object, Object> record = mock(MapRecord.class);
                    when(record.getId()).thenReturn(RecordId.of("1760616000000-" + index));
                    when(record.getValue()).thenReturn(Map.of(
                            "room_id", ROOM_ID.toString(),
                            "message_id", UUID.randomUUID().toString(),
                            "content", "question-" + index,
                            "role", "user"));
                    return record;
                })
                .toList();
    }
}
