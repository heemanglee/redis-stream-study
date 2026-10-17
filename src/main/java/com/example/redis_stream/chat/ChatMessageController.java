package com.example.redis_stream.chat;

import com.example.redis_stream.worker.QuestionEventStatusResponse;
import com.example.redis_stream.worker.QuestionEventStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final QuestionEventStatusService questionEventStatusService;

    @PostMapping("/{roomId}/messages")
    public SendMessageResponse send(
            @PathVariable UUID roomId,
            @RequestBody SendMessageRequest request
    ) {
        return chatMessageService.send(roomId, request.content());
    }

    @GetMapping("/{roomId}/messages")
    public List<ChatMessage> findAll(@PathVariable UUID roomId) {
        return chatMessageService.findAll(roomId);
    }

    @GetMapping("/{roomId}/question-events/{streamMessageId}/status")
    public QuestionEventStatusResponse status(
            @PathVariable UUID roomId,
            @PathVariable String streamMessageId
    ) {
        return questionEventStatusService.getStatus(roomId, streamMessageId);
    }
}
