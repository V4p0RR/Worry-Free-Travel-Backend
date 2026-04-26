package pool;

import java.util.concurrent.*;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {

    private static boolean isBuffer = true;

    public static void main(String[] args) throws InterruptedException {
        test1(16, 100, 300, 0.2);
        test2(16, 100, 300, 0.2);
        AdaptiveBufferedThreadPoolExecutor.BasicCalculate basicCalculate = new AdaptiveBufferedThreadPoolExecutor.BasicCalculate();
        basicCalculate.updateCPULoad();
        System.out.println(basicCalculate.getCPULoad());
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
        AdaptiveBufferedThreadPoolExecutor threadPool = createABTP(corePoolSize, maximumPoolSize, queueSize,
                bufferDegree, isPreventRejection);
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = (AdaptiveBufferedThreadPoolExecutor.CountPolicy) threadPool
                .getRejectedExecutionHandler();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        IOTask();
                    }
                });
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
        AdaptiveBufferedThreadPoolExecutor threadPool = createABTP(corePoolSize, maximumPoolSize, queueSize,
                bufferDegree, isPreventRejection, threadLoadJudge, cpuLoadJudge);
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = (AdaptiveBufferedThreadPoolExecutor.CountPolicy) threadPool
                .getRejectedExecutionHandler();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        IOTask();
                    }
                });
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

    public static void JDKTP_Test(int corePoolSize, int maximumPoolSize, int queueSize) {
        CountPolicy countPolicy = new CountPolicy();
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize), new CountPolicy());
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        IOTask();
                    }
                });
            }
            System.out.print("(" + (i + 1) * 200 + "," + threadPool.getPoolSize() + ","
                    + (System.currentTimeMillis() - start) + "," + countPolicy.getCount() + ")");
            if (i != 9) {
                System.out.print("->");
            }
            if (isBuffer) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        threadPool.shutdown();
    }

    public static void TTP_Test(int corePoolSize, int maximumPoolSize, int queueSize) throws InterruptedException {
        AdaptiveBufferedThreadPoolExecutor TTP = createTomcatThreadPool(corePoolSize, maximumPoolSize, queueSize);
        AdaptiveBufferedThreadPoolExecutor.CountPolicy countPolicy = (AdaptiveBufferedThreadPoolExecutor.CountPolicy) TTP
                .getRejectedExecutionHandler();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 200; j++) {
                TTP.execute(new Runnable() {
                    @Override
                    public void run() {
                        IOTask();
                    }
                });
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
            double bufferDegree, boolean isPreventRejection) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    private final String namePrefix = "custom-thread-pool-";

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName(namePrefix + threadNumber.getAndIncrement());
                        t.setDaemon(false); // 非守护线程
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    }
                }, new AdaptiveBufferedThreadPoolExecutor.CountPolicy(), bufferDegree, isPreventRejection, 5, 0.5, 10,
                100, 3);
    }

    public static AdaptiveBufferedThreadPoolExecutor createABTP(int corePoolSize, int maximumPoolSize, int queueSize,
            double bufferDegree, boolean isPreventRejection, Integer threadLoadJudge, double cpuLoadJudge) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    private final String namePrefix = "custom-thread-pool-";

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName(namePrefix + threadNumber.getAndIncrement());
                        t.setDaemon(false); // 非守护线程
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    }
                }, new AdaptiveBufferedThreadPoolExecutor.CountPolicy(), bufferDegree, isPreventRejection,
                threadLoadJudge, cpuLoadJudge, 10, 100, 3);
    }

    public static AdaptiveBufferedThreadPoolExecutor createTomcatThreadPool(int corePoolSize, int maximumPoolSize,
            int queueSize) {
        return new AdaptiveBufferedThreadPoolExecutor(corePoolSize, maximumPoolSize, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    private final String namePrefix = "custom-thread-pool-";

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName(namePrefix + threadNumber.getAndIncrement());
                        t.setDaemon(false); // 非守护线程
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    }
                }, new AdaptiveBufferedThreadPoolExecutor.CountPolicy(), 0);
    }

    public static void IOTask() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class CountPolicy implements RejectedExecutionHandler {
        private static AtomicInteger count = new AtomicInteger(0);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }
}
