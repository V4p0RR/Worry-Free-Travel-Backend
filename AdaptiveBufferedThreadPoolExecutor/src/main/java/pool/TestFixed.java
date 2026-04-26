package pool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestFixed {

    private static boolean isBuffer = true;

    public static void main(String[] args) throws InterruptedException {
        test1(16, 100, 300, 0.2);
        test2(16, 100, 300, 0.2);
    }

    // 缓冲扩展策略->(已执行任务数, 线程数, 执行时间, 拒绝策略执行次数)
    // 测试ABTP在不同阻塞度条件下的表现情况
    public static void test1(int corePoolSize, int maximumPoolSize, int queueSize, double bufferDegreeBase)
            throws InterruptedException {
        for (int i = 0; i <= 5; i++) {
            String bufferDegree = String.format("%.1f", bufferDegreeBase * i);
            System.out.print("ABTP-" + bufferDegree + ":");
            ABTP_Test(corePoolSize, maximumPoolSize, queueSize, bufferDegreeBase * i, false);
            System.out.println();
            Thread.sleep(1000);
        }
        System.out.print("JDKTP:");
        JDKTP_Test(corePoolSize, maximumPoolSize, queueSize);
        Thread.sleep(1000);
        System.out.print("\nTTP:");
        TTP_Test(corePoolSize, maximumPoolSize, queueSize);
    }

    // 强制入队模块测试->(已执行任务数, 线程数, 执行时间, 拒绝策略执行次数)
    // 测试ABTP的稳定性情况
    public static void test2(int corePoolSize, int maximumPoolSize, int queueSize, double bufferDegreeBase)
            throws InterruptedException {
        System.out.print("BISSJDKTP:");
        ABTP_Test(corePoolSize, maximumPoolSize, queueSize, bufferDegreeBase, true, -1, 1);
        System.out.println();
        System.out.print("BWSJDKTP:");
        ABTP_Test(corePoolSize, maximumPoolSize, queueSize, bufferDegreeBase, true, 100, 1);
        System.out.println();
        System.out.print("RQSJDKTP:");
        ABTP_Test(corePoolSize, maximumPoolSize, queueSize, bufferDegreeBase, true, -1, -1);
        System.out.println();
        System.out.print("JDKTP:");
        JDKTP_Test(corePoolSize, maximumPoolSize, queueSize);
        System.out.println();
        System.out.print("TTP:");
        TTP_Test(corePoolSize, maximumPoolSize, queueSize);
    }

    public static void ABTP_Test(int corePoolSize, int maximumPoolSize, int queueSize, double bufferDegree,
            boolean isPreventRejection) throws InterruptedException {
        // 每次测试新建 CountPolicy 实例，避免跨轮次数据污染
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor threadPool = createABTP(corePoolSize, maximumPoolSize, queueSize,
                bufferDegree, isPreventRejection, countPolicy);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(() -> IOTask());
            }
            System.out.print("(" + (i + 1) * 200 + "," + threadPool.getPoolSize() + ","
                    + (System.currentTimeMillis() - start) + "," + countPolicy.getCount() + ")");
            if (i != 9) {
                System.out.print("->");
            }
            if (isBuffer) {
                Thread.sleep(100);
            }
        }
        threadPool.shutdown();
    }

    public static void ABTP_Test(int corePoolSize, int maximumPoolSize, int queueSize, double bufferDegree,
            boolean isPreventRejection, Integer threadLoadJudge, double cpuLoadJudge) throws InterruptedException {
        // 每次测试新建 CountPolicy 实例，避免跨轮次数据污染
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor threadPool = createABTP(corePoolSize, maximumPoolSize, queueSize,
                bufferDegree, isPreventRejection, threadLoadJudge, cpuLoadJudge, countPolicy);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(() -> IOTask());
            }
            System.out.print("(" + (i + 1) * 200 + "," + threadPool.getPoolSize() + ","
                    + (System.currentTimeMillis() - start) + "," + countPolicy.getCount() + ")");
            if (i != 9) {
                System.out.print("->");
            }
            if (isBuffer) {
                Thread.sleep(100);
            }
        }
        threadPool.shutdown();
    }

    public static void JDKTP_Test(int corePoolSize, int maximumPoolSize, int queueSize) throws InterruptedException {
        // 修复：countPolicy 和线程池使用同一个实例
        CountPolicy countPolicy = new CountPolicy();
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize), countPolicy);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(() -> IOTask());
            }
            System.out.print("(" + (i + 1) * 200 + "," + threadPool.getPoolSize() + ","
                    + (System.currentTimeMillis() - start) + "," + countPolicy.getCount() + ")");
            if (i != 9) {
                System.out.print("->");
            }
            if (isBuffer) {
                Thread.sleep(100);
            }
        }
        threadPool.shutdown();
    }

    public static void TTP_Test(int corePoolSize, int maximumPoolSize, int queueSize) throws InterruptedException {
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor TTP = createTomcatThreadPool(corePoolSize, maximumPoolSize, queueSize,
                countPolicy);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                TTP.execute(() -> IOTask());
            }
            System.out.print("(" + (i + 1) * 200 + "," + TTP.getPoolSize() + "," + (System.currentTimeMillis() - start)
                    + "," + countPolicy.getCount() + ")");
            if (i != 9) {
                System.out.print("->");
            }
            if (isBuffer) {
                Thread.sleep(100);
            }
        }
        TTP.shutdown();
    }

    public static AdaptiveBufferedThreadPoolExecutor createABTP(int corePoolSize, int maximumPoolSize, int queueSize,
            double bufferDegree, boolean isPreventRejection, AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                newThreadFactory(),
                countPolicy, bufferDegree, isPreventRejection, 5, 0.5, 10, 100, 3);
    }

    public static AdaptiveBufferedThreadPoolExecutor createABTP(int corePoolSize, int maximumPoolSize, int queueSize,
            double bufferDegree, boolean isPreventRejection, Integer threadLoadJudge, double cpuLoadJudge,
            AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                newThreadFactory(),
                countPolicy, bufferDegree, isPreventRejection, threadLoadJudge, cpuLoadJudge, 10, 100, 3);
    }

    public static AdaptiveBufferedThreadPoolExecutor createTomcatThreadPool(int corePoolSize, int maximumPoolSize,
            int queueSize, AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                newThreadFactory(),
                countPolicy, 0);
    }

    private static ThreadFactory newThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r);
            t.setName("custom-thread-pool-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
    }

    public static void IOTask() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // 修复：count 改为实例变量，避免多个实例共享同一计数
    public static class CountPolicy implements java.util.concurrent.RejectedExecutionHandler {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }
}
