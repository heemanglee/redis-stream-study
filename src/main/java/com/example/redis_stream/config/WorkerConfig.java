package com.example.redis_stream.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.redis_stream.worker.WorkerProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService questionEventWorkerExecutor() {
        // Consumer group에 최대 5개의 worker 가 동시에 실행될 수 있도록 한다.
        return Executors.newFixedThreadPool(5);
    }
}
