package com.example.redis_stream.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redis-stream.worker")
public record WorkerProperties(String group, Duration blockTimeout, String consumerName) {
}
