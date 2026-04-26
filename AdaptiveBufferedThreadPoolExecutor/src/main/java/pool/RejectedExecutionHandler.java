package pool;

/**
 * 拒绝策略
 * 当任务被拒绝时 怎么处理
 */
public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, AdaptiveBufferedThreadPoolExecutor executor);
}
