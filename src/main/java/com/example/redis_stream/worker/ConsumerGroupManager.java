package com.example.redis_stream.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.RedisBusyException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConsumerGroupManager {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupManager.class);

    private final StringRedisTemplate redisTemplate;
    private final WorkerProperties workerProperties;

    public ConsumerGroupManager(StringRedisTemplate redisTemplate, WorkerProperties workerProperties) {
        this.redisTemplate = redisTemplate;
        this.workerProperties = workerProperties;
    }

    public void ensureGroup(String streamKey) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), workerProperties.group());
            log.info("event=REDIS_CONSUMER_GROUP_CREATED redis_command=XGROUP stream_key={} consumer_group={}",
                    streamKey, workerProperties.group()
            );
        } catch (RedisSystemException exception) {
            if (isBusyGroup(exception)) {
//                log.info("event=REDIS_CONSUMER_GROUP_EXISTS redis_command=XGROUP stream_key={} consumer_group={}",
//                        streamKey, workerProperties.group()
//                );
                return;
            }
            throw exception;
        }
    }

    private boolean isBusyGroup(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RedisBusyException
                    || (current.getMessage() != null && current.getMessage().contains("BUSYGROUP"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
