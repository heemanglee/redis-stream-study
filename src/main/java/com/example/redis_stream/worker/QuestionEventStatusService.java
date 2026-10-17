package com.example.redis_stream.worker;

import com.example.redis_stream.chat.ChatMessage;
import com.example.redis_stream.redis.RedisKeyFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class QuestionEventStatusService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final ObjectMapper objectMapper;
    private final WorkerProperties workerProperties;

    public QuestionEventStatusService(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            ObjectMapper objectMapper,
            WorkerProperties workerProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.objectMapper = objectMapper;
        this.workerProperties = workerProperties;
    }

    public QuestionEventStatusResponse getStatus(UUID roomId, String streamMessageId) {
        String streamKey = keyFactory.questionEventsKey(roomId);
        PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                streamKey,
                workerProperties.group(),
                Range.closed(streamMessageId, streamMessageId),
                1
        );
        boolean pending = pendingMessages != null && !pendingMessages.isEmpty();
        boolean assistantSaved = hasAssistantMessage(roomId, streamMessageId);
        boolean acknowledged = !pending && assistantSaved;
        String status = acknowledged ? "ACKED" : pending ? "PENDING" : "PUBLISHED";
        return new QuestionEventStatusResponse(streamMessageId, status, pending, assistantSaved, acknowledged);
    }

    private boolean hasAssistantMessage(UUID roomId, String streamMessageId) {
        List<String> values = redisTemplate.opsForList().range(keyFactory.messagesKey(roomId), 0, -1);
        if (values == null) {
            return false;
        }
        return values.stream().map(this::readMessage).anyMatch(message ->
                message != null
                        && "assistant".equals(message.role())
                        && streamMessageId.equals(message.streamMessageId())
        );
    }

    private ChatMessage readMessage(String value) {
        try {
            return objectMapper.readValue(value, ChatMessage.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
