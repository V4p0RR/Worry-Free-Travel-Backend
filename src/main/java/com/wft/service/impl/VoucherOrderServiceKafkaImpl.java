package com.wft.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wft.dto.Result;
import com.wft.entity.SeckillVoucher;
import com.wft.entity.VoucherOrder;
import com.wft.mapper.VoucherOrderMapper;
import com.wft.service.ISeckillVoucherService;
import com.wft.service.IVoucherOrderService;
import com.wft.utils.KafkaConstants;
import com.wft.utils.RedisConstants;
import com.wft.utils.RedisIdWorker;
import com.wft.utils.UserHolder;
import com.wft.utils.ThreadPoolConstants;

import cn.hutool.core.util.BooleanUtil;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;
import pool.AdaptiveBufferedThreadPoolExecutor;

/**
 * 秒杀优惠券实现类
 * kafka+本地缓冲队列+负载感知动态线程池
 */
@Service
@Log4j2
@Primary
@ConditionalOnProperty(name = "wft.seckill.mode", havingValue = "kafka")
public class VoucherOrderServiceKafkaImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
    implements IVoucherOrderService {
  @Resource
  private ISeckillVoucherService seckillVoucherService;
  @Resource
  private RedisIdWorker redisIdWorker;
  @Resource
  private StringRedisTemplate stringRedisTemplate;
  @Resource
  private RedissonClient redissonClient;

  // 创建一个kafka生产者
  private KafkaProducer<String, String> kafkaProducer;
  // 创建一个kafka消费者
  private KafkaConsumer<String, String> kafkaConsumer;
  // 创建一个对象映射器
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  // 队列70%时暂停
  private static final double QUEUE_PAUSE_THRESHOLD = 0.7;
  // bufferQueue大小
  private static final int BUFFER_QUEUE_SIZE = 3000;

  // 创建一个队列 用来保存需要提交的偏移量
  private final ConcurrentLinkedQueue<Map<TopicPartition, OffsetAndMetadata>> commitQueue = new ConcurrentLinkedQueue<>();

  /**
   * 封装 TopicPartition 和 offset内部类
   */
  private static class TopicPartitionOffset {
    private final String topic;
    private final int partition;
    private final long offset;

    public TopicPartitionOffset(String topic, int partition, long offset) {
      this.topic = topic;
      this.partition = partition;
      this.offset = offset;
    }
  }

  // Lua脚本
  // 放入静态内部类 加载类时就初始化
  private static final DefaultRedisScript<Long> SECKILL_KAFKA_SCRIPT;
  static {
    SECKILL_KAFKA_SCRIPT = new DefaultRedisScript<>();
    SECKILL_KAFKA_SCRIPT.setLocation(new ClassPathResource("seckill-kafka.lua"));
    SECKILL_KAFKA_SCRIPT.setResultType(Long.class);
  }
  // 创建一个ladbtp线程池 用于处理秒杀订单
  // 用于异步写库 io线程池
  private static final AdaptiveBufferedThreadPoolExecutor SECKILL_ORDER_EXECUTOR = new AdaptiveBufferedThreadPoolExecutor(
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

  /**
   * 初始化方法
   * 在应用启动时，创建消费者
   */
  @PostConstruct
  private void init() {
    // 初始化消费者
    kafkaConsumer = new KafkaConsumer<>(KafkaConstants.KAFKA_CONSUMER_CONFIG);
    // 初始化生产者
    kafkaProducer = new KafkaProducer<>(KafkaConstants.KAFKA_PRODUCER_CONFIG);
    final VoucherOrderKafkaConsumer CONSUMER = new VoucherOrderKafkaConsumer(
        kafkaConsumer, // kafka消费者
        SECKILL_ORDER_EXECUTOR // 线程池
    );
    // 启动消费者
    CONSUMER.start();
  }

  /**
   * 秒杀优惠券 优化版
   */
  @Override
  public Result seckillVoucher(Long voucherId) {
    // 先检查 Redis 中是否有库存数据
    String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
    if (BooleanUtil.isFalse(stringRedisTemplate.hasKey(stockKey))) {
      // 从数据库查询秒杀券信息
      SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
      if (seckillVoucher != null) {
        // 初始化库存到 Redis
        // setnx 是幂等写 只有key不存在时能写 第一次能写 后面的全部失败
        Integer stock = seckillVoucher.getStock();
        if (stock != null) {
          stringRedisTemplate.opsForValue().set(stockKey, stock.toString());
        }
        // 初始化订单集合
        // stringRedisTemplate.opsForSet().add(RedisConstants.SECKILL_ORDER_KEY +
        // voucherId);
      }
    }
    // 先获取userId和orderId
    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");
    // 1.执行lua脚本 判断购买资格
    Long result = stringRedisTemplate.execute(SECKILL_KAFKA_SCRIPT,
        Collections.emptyList(),
        voucherId.toString(),
        userId.toString(),
        String.valueOf(orderId));
    // 返回非0，没有购买资格

    int r = result.intValue();
    if (r != 0) {
      // 如果返回值不为0，说明没有购买资格
      return Result.fail(r == 1 ? "库存不足" : "一人一单，请勿重复下单！");
    }
    // 返回值为0,可购买 把下单信息传入kafka
    // 构建订单对象
    VoucherOrder voucherOrder = new VoucherOrder();
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);

    // 序列化并发送
    try {
      String orderJson = OBJECT_MAPPER.writeValueAsString(voucherOrder);
      kafkaProducer.send(
          new ProducerRecord<>("voucher-order", userId.toString(), orderJson),
          (metadata, ex) -> {
            if (ex != null)
              log.error("Kafka 发送失败,订单ID:{}", orderId, ex);
          });
    } catch (JsonProcessingException e) {
      log.error("订单序列化失败", e);
      return Result.fail("系统错误");
    }
    // 返回订单id
    return Result.ok(orderId);
  }

  /**
   * 创建代金券订单
   * 
   * @param voucherOrder
   */
  public void createVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();
    Long voucherId = voucherOrder.getVoucherId();
    // 创建锁对象
    RLock redisLock = redissonClient.getLock("lock:order:" + userId);
    // 尝试获取锁
    boolean isLock = redisLock.tryLock();
    // 判断
    if (!isLock) {
      // 获取锁失败，直接返回失败或者重试 不可重入
      log.error("不允许重复下单！");
      return;
    }

    try {
      // 5.1.查询订单
      int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
      // 5.2.判断是否存在
      if (count > 0) {
        // 用户已经购买过了
        log.error("不允许重复下单！");
        return;
      }

      // 6.扣减库存 乐观锁
      boolean success = seckillVoucherService.update()
          .setSql("stock = stock - 1") // set stock = stock - 1
          .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
          .update();
      if (!success) {
        // 扣减失败
        log.error("库存不足！");
        return;
      }

      // 7.创建订单
      save(voucherOrder);
    } finally {
      // 释放锁
      redisLock.unlock();
    }
  }

  private class VoucherOrderKafkaConsumer {

    private final KafkaConsumer<String, String> consumer;
    private final AdaptiveBufferedThreadPoolExecutor threadPool;

    // 本地阻塞队列 控制拉取消息的速度
    // ladbtp线程池稳定性强，所以bufferQueue完全可以比我们的线程池队列长度 + 最大线程数大一些
    private final BlockingQueue<ConsumerRecord<String, String>> bufferQueue = new LinkedBlockingQueue<>(
        BUFFER_QUEUE_SIZE);
    // 已完成记录集合
    private final ConcurrentLinkedQueue<TopicPartitionOffset> ackQueue = new ConcurrentLinkedQueue<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoucherOrderKafkaConsumer(KafkaConsumer<String, String> consumer,
        AdaptiveBufferedThreadPoolExecutor threadPool) {
      this.consumer = consumer;
      this.threadPool = threadPool;

    }

    public void start() {
      consumer.subscribe(Collections.singletonList("voucher-order"));
      new Thread(this::consumeLoop, "kafka-consume-loop").start();// 消费主线程
      new Thread(this::dispatchToThreadPool, "kafka-dispatch-loop").start();// 消费执行线程
    }

    /**
     * 消费者循环 用于批量拉消息 然后入队(本地缓冲区)
     */
    private void consumeLoop() {
      while (true) {
        // 这里是一个简化版的Backpressure：如果当前线程池太忙，就暂停拉取
        // 避免“Kafka消息一直拉、线程池已经打满”的过载失控
        // 背压（Backpressure）是一种流量控制机制
        // 核心逻辑是 当下游消费者处理速度跟不上上游生产者的产生速度时
        // 下游反馈压力，强制上游降速。
        if (shouldPausePulling()) { // 如果缓冲区已满，则暂停拉取消息
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) { // 休眠状态设置中断位 直接终止
            log.error("Thread interrupted", e);
            Thread.currentThread().interrupt();
            break;
          }
          continue;
        }
        // 拉取消息 批量
        // 这里设置poll最多拉2000条 超时时间100ms 超过返回空
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<String, String> record : records) { // 遍历拉取到的消息
          // 拉到的消息不会直接执行，而是先放入本地内存队列，后续由另一个LADBTP去处理
          if (!bufferQueue.offer(record)) {
            // 缓冲区已满 如果真的满了，那就说明超出可承载的范围了，可以做一些特殊处理
            // 比如重试或丢弃
            log.warn("缓冲区已满，丢弃消息： {}", record.key());
          }
        }
        // 批量提交
        Map<TopicPartition, OffsetAndMetadata> pending;
        while ((pending = commitQueue.poll()) != null) {
          consumer.commitAsync(pending, (o, e) -> {
            if (e != null)
              log.error("批量提交 offset 失败", e);
          });
        }
      }
    }

    /**
     * 处理消息
     * 从队列批量取消息 → 丢进线程池并发处理 → 成功后记录 offset → 最后统一提交
     */
    private void dispatchToThreadPool() {
      while (true) {
        try {
          // 存放消息的list 批量提取一批消息（减少粒度太小的处理浪费）
          List<ConsumerRecord<String, String>> batch = new ArrayList<>();
          // 从缓冲区中取出一条消息 阻塞取 确保有消息（避免空转）
          batch.add(bufferQueue.take());

          // 尽可能多地 drain 出后续消息（非阻塞） 提高吞吐
          // 不是一定取100条，而是能取多少取多少，最多100条 顺手取
          // 限制批处理最大条数，防止一次拉太多
          bufferQueue.drainTo(batch, 100);

          // 创建CompletableFuture列表 批量处理 并获取结果
          List<CompletableFuture<Void>> futures = new ArrayList<>();

          // 遍历取出来的消息 但是执行是并发的
          for (ConsumerRecord<String, String> record : batch) {
            futures.add( // 收集每个任务的future
                // 异步执行 不阻塞当前线程
                CompletableFuture.runAsync(() -> handle(record), threadPool)
                    .whenComplete((v, ex) -> { // 每条消息处理完都会回调
                      if (ex != null) { // 处理失败 打印失败信息
                        log.warn("处理失败，正在重试: {},e:{}", record.key(), ex);
                      } else { // 处理成功 添加到已完成集合askQueue 后面一次性提交
                        ackQueue.offer(new TopicPartitionOffset(
                            record.topic(), record.partition(), record.offset()));
                      }
                    }));
          }

          // 批处理任务全部完成后，统一提交 offset
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
              .whenComplete((v, ex) -> { // 任务全部完成后
                if (ex != null) {
                  log.error("部分任务执行失败，略过 offset 提交", ex);
                } else {
                  periodicCommit(); // 安全批量提交已完成 offset
                }
              });

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("线程被中断，退出 dispatch 线程");
          break;
        } catch (Exception e) {
          log.error("Dispatch error:{}", e);
        }
      }
    }

    /**
     * 处理错误 重试机制
     * 
     * @param record
     */
    private void handle(ConsumerRecord<String, String> record) {
      Exception lastEx = null;
      for (int i = 0; i < 3; i++) {
        try {
          VoucherOrder order = objectMapper.readValue(record.value(), VoucherOrder.class);
          createVoucherOrder(order);
          return; // 成功直接返回
        } catch (Exception e) {
          lastEx = e;
          log.warn("第{}次处理失败: {}", i + 1, record.key());
        }
      }
      // 超过重试次数，打印失败信息 可接入死信
      log.error("消息处理彻底失败: {}, e:{}", record.value(), lastEx);
      // kafkaProducer.send(new ProducerRecord<>("voucher-order-dlq", record.key(),
      // record.value()));
    }

    /**
     * 这里的主要目的是控制消费速度。
     * 这块没有标准答案，想怎么写就怎么写，结合项目背景问chatgpt
     * 可以让LADBTP通过get方法主动暴露指标即可：
     * 1. 当前队列长度
     * 2. 活跃线程数
     * 3. 最大并发上限（可用CPU核数作为边界）
     * 4. BufferFactor（决定线程扩容频率）
     * 6. ...
     * bufferQueue 和线程池队列都纳入监控，容量变了比例自动跟着变
     * 
     * @return
     */
    private boolean shouldPausePulling() {
      int poolQueueSize = threadPool.getQueue().size();
      int poolQueueCapacity = threadPool.getQueue().remainingCapacity() + poolQueueSize;
      boolean poolQueuePressure = poolQueueSize > poolQueueCapacity * QUEUE_PAUSE_THRESHOLD;

      int bufferSize = bufferQueue.size();
      int bufferCapacity = bufferSize + bufferQueue.remainingCapacity();
      boolean bufferPressure = bufferSize > bufferCapacity * QUEUE_PAUSE_THRESHOLD;

      // log.info("拒绝策略执行次数:{}",
      // ((AdaptiveBufferedThreadPoolExecutor.CountPolicy)
      // threadPool.getRejectedExecutionHandler()).getCount());

      return poolQueuePressure || bufferPressure;
    }

    /**
     * 如果是单条记录的提交：
     * 会增加 Kafka I/O 压力，并且 offset 更新不连续，无法充分利用批量拉取优势
     * 并且，如果 handle() 是慢任务，多线程并发时，会导致offset提交顺序不确定
     * 有可能 offset提交早了，但业务处理没完成，这在消费端挂掉后会造成数据丢失
     * 所以最好的方式是通过批量提交
     */
    private void periodicCommit() {
      Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
      TopicPartitionOffset offset;
      while ((offset = ackQueue.poll()) != null) {
        offsets.merge(
            new TopicPartition(offset.topic, offset.partition),
            new OffsetAndMetadata(offset.offset + 1),
            (existing, newer) -> newer.offset() > existing.offset() ? newer : existing);
      }
      if (!offsets.isEmpty()) {
        commitQueue.offer(offsets); // 入队，不直接 commit
      }
    }

  }

}

