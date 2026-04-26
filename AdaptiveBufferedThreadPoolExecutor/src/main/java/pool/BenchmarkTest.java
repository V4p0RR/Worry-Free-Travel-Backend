package pool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ABTP vs JDK ThreadPoolExecutor 全面对比测试
 *
 * 对比维度：
 *  1. 吞吐量      - 相同时间内完成的任务数
 *  2. 拒绝率      - 任务被拒绝的比例
 *  3. 响应延迟    - 任务从提交到开始执行的等待时间
 *  4. 线程扩展速度 - 线程数随负载变化的情况
 *  5. CPU/IO混合  - 不同任务类型下的表现
 *
 * 输出格式：各维度结果汇总表格
 */
public class BenchmarkTest {

    // ── 测试参数 ────────────────────────────────────────────────
    static final int CORE   = 8;
    static final int MAX    = 64;
    static final int QUEUE  = 200;
    static final int ROUNDS = 5; // 每个场景重复次数，取平均

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 线程池全面对比测试 ===");
        System.out.printf("配置：核心线程=%d  最大线程=%d  队列容量=%d%n%n", CORE, MAX, QUEUE);

        System.out.println("【场景1】突发高并发（IO密集型，50ms/任务）");
        sceneBurst(50, 2000);

        System.out.println("\n【场景2】持续高并发（CPU密集型，5ms/任务）");
        sceneBurst(5, 2000);

        System.out.println("\n【场景3】队列溢出压力（任务数远超队列+线程上限）");
        sceneOverflow(50, 5000);

        System.out.println("\n【场景4】响应延迟对比");
        sceneLatency(50, 500);

        System.out.println("\n【场景5】线程扩展速度对比");
        sceneThreadGrowth(50, 1000);
    }

    // ── 场景1/2：吞吐量 & 拒绝率 ────────────────────────────────

    static void sceneBurst(int taskMs, int totalTasks) throws InterruptedException {
        long[] abtp  = avgRun(ROUNDS, () -> runABTP(taskMs, totalTasks, 0.3, false));
        long[] abtpP = avgRun(ROUNDS, () -> runABTP(taskMs, totalTasks, 0.3, true));
        long[] jdk   = avgRun(ROUNDS, () -> runJDK(taskMs, totalTasks));

        System.out.println("  线程池            完成任务    拒绝任务    耗时(ms)");
        System.out.println("  ─────────────────────────────────────────────────");
        printRow("ABTP(无防拒绝)",  abtp);
        printRow("ABTP(防拒绝)",   abtpP);
        printRow("JDK TP",         jdk);
    }

    // ── 场景3：队列溢出 ──────────────────────────────────────────

    static void sceneOverflow(int taskMs, int totalTasks) throws InterruptedException {
        long[] abtp  = avgRun(ROUNDS, () -> runABTP(taskMs, totalTasks, 0.3, false));
        long[] abtpP = avgRun(ROUNDS, () -> runABTP(taskMs, totalTasks, 0.3, true));
        long[] jdk   = avgRun(ROUNDS, () -> runJDK(taskMs, totalTasks));

        System.out.println("  线程池            完成任务    拒绝任务    拒绝率      耗时(ms)");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        printRowWithRate("ABTP(无防拒绝)",  abtp,  totalTasks);
        printRowWithRate("ABTP(防拒绝)",   abtpP, totalTasks);
        printRowWithRate("JDK TP",         jdk,   totalTasks);
    }

    // ── 场景4：响应延迟 ──────────────────────────────────────────

    static void sceneLatency(int taskMs, int totalTasks) throws InterruptedException {
        System.out.println("  线程池            平均延迟(ms)  最大延迟(ms)  拒绝任务");
        System.out.println("  ─────────────────────────────────────────────────────");

        LatencyResult abtp  = runABTPLatency(taskMs, totalTasks, 0.3, false);
        LatencyResult abtpP = runABTPLatency(taskMs, totalTasks, 0.3, true);
        LatencyResult jdk   = runJDKLatency(taskMs, totalTasks);

        System.out.printf("  %-18s %-14.1f %-14d %d%n",
                "ABTP(无防拒绝)", abtp.avgMs(), abtp.maxMs, abtp.rejected);
        System.out.printf("  %-18s %-14.1f %-14d %d%n",
                "ABTP(防拒绝)",  abtpP.avgMs(), abtpP.maxMs, abtpP.rejected);
        System.out.printf("  %-18s %-14.1f %-14d %d%n",
                "JDK TP",        jdk.avgMs(), jdk.maxMs, jdk.rejected);
    }

    // ── 场景5：线程扩展速度 ──────────────────────────────────────

    static void sceneThreadGrowth(int taskMs, int totalTasks) throws InterruptedException {
        System.out.println("  每提交200任务后的线程数快照（格式：线程数）");
        System.out.println("  提交批次:     200   400   600   800   1000");
        System.out.println("  ─────────────────────────────────────────────────");

        System.out.print("  ABTP(0.2):    ");
        runGrowth(taskMs, totalTasks, 0.2, false);

        System.out.print("\n  ABTP(0.5):    ");
        runGrowth(taskMs, totalTasks, 0.5, false);

        System.out.print("\n  ABTP(1.0):    ");
        runGrowth(taskMs, totalTasks, 1.0, false);

        System.out.print("\n  JDK TP:       ");
        runJDKGrowth(taskMs, totalTasks);

        System.out.println();
    }

    // ── 执行函数 ─────────────────────────────────────────────────

    // 返回 [完成任务数, 拒绝任务数, 耗时ms]
    static long[] runABTP(int taskMs, int totalTasks, double bufferDegree, boolean preventRejection) {
        AdaptiveBufferedThreadPoolExecutor.CountPolicy policy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor pool = new AdaptiveBufferedThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("abtp"),
                policy, bufferDegree, preventRejection, 5, 0.5, 10, 200, 3);

        AtomicLong completed = new AtomicLong();
        long start = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            pool.execute(() -> {
                sleep(taskMs);
                completed.incrementAndGet();
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        return new long[]{ completed.get(), policy.getCount(), System.currentTimeMillis() - start };
    }

    static long[] runJDK(int taskMs, int totalTasks) {
        CountPolicy policy = new CountPolicy();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("jdk"), policy);

        AtomicLong completed = new AtomicLong();
        long start = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            pool.execute(() -> {
                sleep(taskMs);
                completed.incrementAndGet();
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        return new long[]{ completed.get(), policy.getCount(), System.currentTimeMillis() - start };
    }

    // 延迟测试
    static LatencyResult runABTPLatency(int taskMs, int totalTasks, double bufferDegree, boolean preventRejection) {
        AdaptiveBufferedThreadPoolExecutor.CountPolicy policy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor pool = new AdaptiveBufferedThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("abtp-lat"),
                policy, bufferDegree, preventRejection, 5, 0.5, 10, 200, 3);

        LatencyResult result = collectLatency(pool, taskMs, totalTasks);
        result.rejected = policy.getCount();
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result;
    }

    static LatencyResult runJDKLatency(int taskMs, int totalTasks) {
        CountPolicy policy = new CountPolicy();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("jdk-lat"), policy);

        LatencyResult result = collectLatency(pool, taskMs, totalTasks);
        result.rejected = policy.getCount();
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result;
    }

    static LatencyResult collectLatency(Executor pool, int taskMs, int totalTasks) {
        AtomicLong totalDelay = new AtomicLong();
        AtomicLong maxDelay   = new AtomicLong();
        AtomicLong count      = new AtomicLong();

        for (int i = 0; i < totalTasks; i++) {
            long submit = System.currentTimeMillis();
            try {
                pool.execute(() -> {
                    long delay = System.currentTimeMillis() - submit;
                    totalDelay.addAndGet(delay);
                    maxDelay.updateAndGet(cur -> Math.max(cur, delay));
                    count.incrementAndGet();
                    sleep(taskMs);
                });
            } catch (RejectedExecutionException ignored) {}
        }

        LatencyResult r = new LatencyResult();
        r.totalDelayMs = totalDelay.get();
        r.maxMs        = maxDelay.get();
        r.taskCount    = count.get();
        return r;
    }

    // 线程扩展快照
    static void runGrowth(int taskMs, int totalTasks, double bufferDegree, boolean preventRejection) throws InterruptedException {
        AdaptiveBufferedThreadPoolExecutor.CountPolicy policy = new AdaptiveBufferedThreadPoolExecutor.CountPolicy();
        AdaptiveBufferedThreadPoolExecutor pool = new AdaptiveBufferedThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("abtp-gr"),
                policy, bufferDegree, preventRejection, 5, 0.5, 10, 200, 3);

        int batch = totalTasks / 5;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < batch; j++) {
                pool.execute(() -> sleep(taskMs));
            }
            System.out.printf("%-6d", pool.getPoolSize());
            Thread.sleep(50);
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    static void runJDKGrowth(int taskMs, int totalTasks) throws InterruptedException {
        CountPolicy policy = new CountPolicy();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                newFactory("jdk-gr"), policy);

        int batch = totalTasks / 5;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < batch; j++) {
                pool.execute(() -> sleep(taskMs));
            }
            System.out.printf("%-6d", pool.getPoolSize());
            Thread.sleep(50);
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    // ── 工具方法 ─────────────────────────────────────────────────

    // 多轮运行取平均值，返回 [完成任务均值, 拒绝任务均值, 耗时均值]
    static long[] avgRun(int rounds, ThrowingSupplier supplier) throws InterruptedException {
        long totalCompleted = 0, totalRejected = 0, totalTime = 0;
        for (int i = 0; i < rounds; i++) {
            Thread.sleep(500); // 轮次间隔，让系统恢复
            long[] r = supplier.get();
            totalCompleted += r[0];
            totalRejected  += r[1];
            totalTime      += r[2];
        }
        return new long[]{ totalCompleted / rounds, totalRejected / rounds, totalTime / rounds };
    }

    static void printRow(String name, long[] r) {
        System.out.printf("  %-18s %-12d %-12d %d%n", name, r[0], r[1], r[2]);
    }

    static void printRowWithRate(String name, long[] r, int total) {
        double rate = total > 0 ? r[1] * 100.0 / total : 0;
        System.out.printf("  %-18s %-12d %-12d %-12.1f%% %d%n", name, r[0], r[1], rate, r[2]);
    }

    static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static ThreadFactory newFactory(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
    }

    @FunctionalInterface
    interface ThrowingSupplier {
        long[] get() throws InterruptedException;
    }

    static class LatencyResult {
        long totalDelayMs;
        long maxMs;
        long taskCount;
        long rejected;

        double avgMs() {
            return taskCount > 0 ? (double) totalDelayMs / taskCount : 0;
        }
    }

    // JDK 线程池的拒绝策略（实例变量版）
    // 显式使用全限定名，避免与 pool.RejectedExecutionHandler 冲突
    static class CountPolicy implements java.util.concurrent.RejectedExecutionHandler {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            count.incrementAndGet();
        }

        public int getCount() { return count.get(); }
    }
}
