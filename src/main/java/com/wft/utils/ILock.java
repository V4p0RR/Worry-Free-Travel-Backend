package com.wft.utils;

/**
 * 分布式锁接口
 * 
 */
public interface ILock {

  /**
   * 尝试获取锁
   * 
   * @param timeoutSec 锁的过期时间，单位秒
   * @return true获取锁成功，false获取锁失败
   */
  boolean tryLock(Long timeoutSec);

  /**
   * 释放锁
   */
  void unlock();

}

