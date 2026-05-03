package flow.datamcpservice.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import pool.AdaptiveBufferedThreadPoolExecutor;
import pool.RejectedExecutionHandler;

public class ThreadPoolConstants {
  // ladbpt参数
  // 核心线程数
  public static final int CORE_POOL_SIZE = 28;
  // 最大线程数
  public static final int MAX_POOL_SIZE = 112;
  // 队列容量
  public static final int QUEUE_SIZE = 2000;
  // 非核心线程存活时间
  public static final long KEEP_ALIVE_TIME = 60L;
  // 非核心线程存活时间单位
  public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
  // 线程工厂
  public static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix = "seckill-thread-pool-";

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setName(namePrefix + threadNumber.getAndIncrement());
      t.setDaemon(false); // 非守护线程
      t.setPriority(Thread.NORM_PRIORITY);
      return t;
    }
  };
  // 拒绝策略
  public static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
  // 缓冲区系数
  public static final double BUFFER_DEGREE = 0.2;
  // 是否防止拒绝
  public static final boolean IS_PREVENT_REJECTION = true;
  // 线程负载判断 建议10-50
  public static final Integer THREAD_LOAD_JUDGE = 10;
  // cpu负载判断 建议0.7-0.8
  public static final double CPU_LOAD_JUDGE = 0.8;
  // cpu退避空转初始等待时间 ms
  public static final long WAIT_TIME = 10L;
  // 阻塞强制入队超时时间 ms
  public static final long TIMEOUT = 100;
  // cpu退避空转强制入队最大重试次数
  public static final Integer MAX_RETRY_ATTEMPTS = 3;

}
