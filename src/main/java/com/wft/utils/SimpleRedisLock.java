package com.wft.utils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import cn.hutool.core.lang.UUID;

/**
 * 简单的分布式锁实现类
 * 初级版
 */
public class SimpleRedisLock implements ILock {

  private String name;// 锁名字，由调用者传入
  private StringRedisTemplate stringRedisTemplate;

  private static final String KEY_PREFIX = "lock:"; // 锁的前缀
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-"; // 线程标识前缀
  // 加载lua脚本
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

  /**
   * 构造函数 接收name和StringRedisTemplate
   * 
   * @param name
   * @param stringRedisTemplate
   */
  public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * 获取锁的key
   *
   * @return 锁的key
   */
  private String getLockKey() {
    return KEY_PREFIX + name;
  }

  @Override
  public boolean tryLock(Long timeoutSec) {
    // 获取线程标识
    String id = ID_PREFIX + Thread.currentThread().getId();
    Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(getLockKey(), id, timeoutSec,
        TimeUnit.SECONDS);
    // 防止自动拆箱空指针
    return Boolean.TRUE.equals(success);
  }

  // @Override
  // public void unlock() {
  // // 先获取线程标识
  // String id = ID_PREFIX + Thread.currentThread().getId();
  // // 获取锁的值
  // String value = stringRedisTemplate.opsForValue().get(getLockKey());
  // // 如果相同 才能释放锁
  // if (id.equals(value)) {
  // stringRedisTemplate.delete(getLockKey());
  // }
  // }

  @Override
  public void unlock() {
    stringRedisTemplate.execute(UNLOCK_SCRIPT,
        Collections.singletonList(getLockKey()), // 返回单元素的集合
        ID_PREFIX + Thread.currentThread().getId()// 线程标识
    );
  }

}

