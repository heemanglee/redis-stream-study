package com.example.redis_stream.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.redis_stream.chat.ChatMessage;
import com.example.redis_stream.chat.QuestionEvent;
import com.example.redis_stream.openai.OpenAiClient;
import com.example.redis_stream.redis.RedisKeyFactory;

import tools.jackson.databind.ObjectMapper;

@Component
public class QuestionEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuestionEventProcessor.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WorkerProperties workerProperties;

    public QuestionEventProcessor(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper,
            Clock clock,
            WorkerProperties workerProperties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.workerProperties = workerProperties;
    }

    public void process(QuestionEvent event, String streamMessageId) {
        Instant startedAt = Instant.now(clock);
        log.info("event=REDIS_EVENT_CONSUMED redis_command=XREADGROUP stream_key={} room_id={} message_id={} role={} stream_message_id={} consumer_group={}",
                keyFactory.questionEventsKey(event.roomId()), event.roomId(), event.messageId(), event.role(),
                streamMessageId, workerProperties.group()
        );
        try {
            log.info("event=LLM_REQUEST_STARTED room_id={} message_id={} stream_message_id={} model=gpt-5.6-luna reasoning=low",
                    event.roomId(), event.messageId(), streamMessageId
            );
            String answer = openAiClient.generate(event.content());
            log.info("event=LLM_RESPONSE_RECEIVED room_id={} message_id={} stream_message_id={} elapsed_ms={}",
                    event.roomId(), event.messageId(), streamMessageId,
                    java.time.Duration.between(startedAt, Instant.now(clock)).toMillis()
            );

            UUID assistantMessageId = UUID.randomUUID();
            ChatMessage assistant = ChatMessage.create(assistantMessageId, answer, "assistant", streamMessageId, Instant.now(clock));
            String messagesKey = keyFactory.messagesKey(event.roomId());
            redisTemplate.opsForList().rightPush(messagesKey, objectMapper.writeValueAsString(assistant));
            log.info("event=REDIS_MESSAGE_SAVED redis_command=RPUSH redis_key={} room_id={} message_id={} role=assistant stream_message_id={} created_at={}",
                    messagesKey, event.roomId(), assistantMessageId, streamMessageId, assistant.createdAt()
            );

            String streamKey = keyFactory.questionEventsKey(event.roomId());
            redisTemplate.opsForStream().acknowledge(streamKey, workerProperties.group(), streamMessageId);
            log.info("event=REDIS_EVENT_ACKNOWLEDGED redis_command=XACK stream_key={} room_id={} stream_message_id={} consumer_group={}",
                    streamKey, event.roomId(), streamMessageId, workerProperties.group()
            );
            log.info("event=MESSAGE_PROCESSING_COMPLETED room_id={} user_message_id={} assistant_message_id={} stream_message_id={} elapsed_ms={}",
                    event.roomId(), event.messageId(), assistantMessageId, streamMessageId,
                    java.time.Duration.between(startedAt, Instant.now(clock)).toMillis()
            );
        } catch (RuntimeException exception) {
            log.error("event=MESSAGE_PROCESSING_FAILED room_id={} message_id={} stream_message_id={} error_type={} error_message={}",
                    event.roomId(), event.messageId(), streamMessageId, exception.getClass().getSimpleName(), exception.getMessage()
            );
            throw exception;
        }
    }
}
