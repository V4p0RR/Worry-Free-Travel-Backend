package flow.datamcpservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import pool.AdaptiveBufferedThreadPoolExecutor;
import flow.datamcpservice.config.ThreadPoolConstants;

@Configuration
public class ThreadPoolExecutorConfig {

    private static AtomicInteger threadCount = new AtomicInteger(0);

    @Bean
    public AdaptiveBufferedThreadPoolExecutor getAdaptiveBufferedThreadPoolExecutor() {
        return new AdaptiveBufferedThreadPoolExecutor(
                ThreadPoolConstants.CORE_POOL_SIZE, // 核心线程数
                ThreadPoolConstants.MAX_POOL_SIZE, // 最大线程数
                ThreadPoolConstants.KEEP_ALIVE_TIME, // 线程存活时间 60s
                ThreadPoolConstants.TIME_UNIT, // 单位 秒 存活时间的
                new LinkedBlockingQueue<>(ThreadPoolConstants.QUEUE_SIZE), // 阻塞队列
                ThreadPoolConstants.THREAD_FACTORY, // 线程工厂
                ThreadPoolConstants.REJECTED_EXECUTION_HANDLER, // 拒绝策略
                ThreadPoolConstants.BUFFER_DEGREE, // buffer degree
                ThreadPoolConstants.IS_PREVENT_REJECTION, // isPreventRejection
                ThreadPoolConstants.THREAD_LOAD_JUDGE, // 线程负载阈值
                ThreadPoolConstants.CPU_LOAD_JUDGE, // cpu负载阈值
                ThreadPoolConstants.WAIT_TIME, // 退避空转初始等待时间 ms
                ThreadPoolConstants.TIMEOUT, // 阻塞等待最大等待时间 ms
                ThreadPoolConstants.MAX_RETRY_ATTEMPTS // cpu退避空转最大重试次数
        );

    }
}