package com.example.redis_stream.chat;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/{roomId}/messages")
    public SendMessageResponse send(
            @PathVariable UUID roomId,
            @RequestBody SendMessageRequest request
    ) {
        return chatMessageService.send(roomId, request.content());
    }

    @GetMapping
    public List<ChatMessage> findAll(@PathVariable UUID roomId) {
        return chatMessageService.findAll(roomId);
    }
}
