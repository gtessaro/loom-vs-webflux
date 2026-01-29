package com.seuapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor compartilhado de Virtual Threads.
 * Bom para IO bloqueante em alto tráfego (cada task ganha sua virtual thread).
 */
@Configuration
public class VirtualThreadExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
