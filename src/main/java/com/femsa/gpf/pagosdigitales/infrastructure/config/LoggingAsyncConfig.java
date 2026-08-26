package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuracion de ejecucion asincrona para auditoria de logs.
 */
@Configuration
@EnableAsync
public class LoggingAsyncConfig {

    /**
     * Define el pool de hilos para persistencia de logs.
     *
     * @return executor asincrono de auditoria
     */
    @Bean(name = "loggingTaskExecutor")
    public ThreadPoolTaskExecutor loggingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("log-audit-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
