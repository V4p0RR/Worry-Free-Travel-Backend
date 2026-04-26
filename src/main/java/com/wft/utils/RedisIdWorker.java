package com.wft.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis全局唯一ID生成器
 */
@Component
public class RedisIdWorker {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  // 开始时间戳 2025-01-01 00:00:00 以秒为单位
  private final static long BEGIN_TIMESTAMP = 1735689600L;
  private final static int COUNT_BITS = 32; // 时间戳左移位数

  /**
   * 生成全局唯一ID
   * 
   * @param keyPrefix 前缀
   * @return
   */
  public long nextId(String keyPrefix) {
    // 1.生成时间戳
    LocalDateTime now = LocalDateTime.now(); // 获取当前时间戳 精确到秒
    long nowSecond = now.toEpochSecond(java.time.ZoneOffset.UTC);
    long timestamp = nowSecond - BEGIN_TIMESTAMP; // 计算时间戳
    // 2.生成序列号
    String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));// 格式化当前时间为 yyyy:MM:dd
    // 方便统计每日销量
    // 自增长 默认加一
    @SuppressWarnings("null")
    long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
    // 3.拼接并返回 64位
    return timestamp << COUNT_BITS | count;
  }
}

