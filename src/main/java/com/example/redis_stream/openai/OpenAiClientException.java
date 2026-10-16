package com.example.redis_stream.openai;

public class OpenAiClientException extends RuntimeException {

    public OpenAiClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenAiClientException(String message) {
        super(message);
    }
}
