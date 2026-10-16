package com.example.redis_stream.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.redis_stream.worker.WorkerProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {
}
