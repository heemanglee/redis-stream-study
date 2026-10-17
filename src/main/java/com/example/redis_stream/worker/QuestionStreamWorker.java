package com.example.redis_stream.worker;

import com.example.redis_stream.chat.QuestionEvent;
import com.example.redis_stream.redis.RedisKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Component
public class QuestionStreamWorker {

    private static final Logger log = LoggerFactory.getLogger(QuestionStreamWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final ConsumerGroupManager consumerGroupManager;
    private final QuestionEventProcessor processor;
    private final WorkerProperties workerProperties;
    private final ExecutorService workerExecutor;

    public QuestionStreamWorker(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            ConsumerGroupManager consumerGroupManager,
            QuestionEventProcessor processor,
            WorkerProperties workerProperties,
            ExecutorService workerExecutor) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.consumerGroupManager = consumerGroupManager;
        this.processor = processor;
        this.workerProperties = workerProperties;
        this.workerExecutor = workerExecutor;
    }

    @Scheduled(fixedDelayString = "${redis-stream.worker.poll-delay:1000}")
    public void poll() {
        Set<String> roomIds = redisTemplate.opsForSet().members(keyFactory.roomIndexKey());
        if (roomIds == null) {
            return;
        }
        for (String roomId : roomIds) {
            consumeRoom(UUID.fromString(roomId));
        }
    }

    private void consumeRoom(UUID roomId) {
        String streamKey = keyFactory.questionEventsKey(roomId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(streamKey))) {
            return;
        }
        try {
            consumerGroupManager.ensureGroup(streamKey);
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(
                            Consumer.from(workerProperties.group(), workerProperties.consumerName()),
                            StreamReadOptions.empty()
                                    .count(5)
                                    .block(workerProperties.blockTimeout()),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );

            if (records == null) {
                return;
            }

            for (MapRecord<String, Object, Object> record : records) {
                workerExecutor.execute(() -> processRecord(record));
            }
        } catch (RuntimeException exception) {
            log.error("event=WORKER_POLL_FAILED stream_key={} room_id={} error_type={} error_message={}",
                    streamKey, roomId, exception.getClass().getSimpleName(), exception.getMessage()
            );
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        processor.process(
                new QuestionEvent(
                        UUID.fromString(String.valueOf(values.get("room_id"))),
                        UUID.fromString(String.valueOf(values.get("message_id"))),
                        String.valueOf(values.get("content")),
                        String.valueOf(values.get("role"))
                ),
                record.getId().getValue()
        );
    }
}
