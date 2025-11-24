package no.kobler.rtb.config;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;

@Configuration
public class AsyncConfiguration {

    @Bean(name = "bidExecutor")
    public ThreadPoolTaskExecutor bidExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("bid-exec-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "bidExecutorService")
    public ExecutorService bidExecutorService(@Qualifier("bidExecutor") ThreadPoolTaskExecutor bidExecutor) {
        return bidExecutor.getThreadPoolExecutor();
    }
}

