package com.wft.service.impl;

import com.wft.dto.Result;
import com.wft.entity.Shop;
import com.wft.mapper.ShopMapper;
import com.wft.service.IShopService;
import com.wft.utils.RedisConstants;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 根据id查询商铺信息
   */
  public Result queryShopById(Long id) {
    // 缓存穿透方案
    // queryWithPassThrough(id);
    // 互斥锁方案 同时解决穿透和击穿
    Shop shop = queryWithMutex(id);
    if (shop == null) {
      return Result.fail("店铺不存在");
    }
    // 如果查询到了shop，直接返回
    return Result.ok(shop);
  }

  /**
   * 查询商铺信息，使用缓存穿透解决方案
   * 
   * @return
   */
  @SuppressWarnings("unused")
  private Result queryWithPassThrough(Long id) {
    // 先定义shop的id模版
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    // 先从redis中查询shop 以String存储
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    // 如果是空值，直接返回不存在信息
    if ("".equals(shopJson)) {
      return Result.fail("店铺不存在");
    }
    // 如果存在且不为空值，直接返回shop对象
    if (StrUtil.isNotBlank(shopJson)) {
      Shop shop = JSONUtil.toBean(shopJson, Shop.class);
      return Result.ok(shop);
    }

    // 如果不存在，在数据库中查询
    Shop shop = getById(id);
    // 从数据库中查到了，直接返回 并存入redis
    if (shop != null) {
      // 将shop对象转换为JSON字符串
      String shopStr = JSONUtil.toJsonStr(shop);
      // 将shop存入redis 设置过期时间30分钟
      stringRedisTemplate.opsForValue().set(key, shopStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
      return Result.ok(shop);
    }
    // 如果数据库没查到，向redis写入空值，防止缓存穿透
    stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    // 返回不存在信息
    return Result.fail("店铺不存在");
  }

  /**
   * 查询商铺信息，使用互斥锁解决缓存击穿问题
   * 并解决缓存穿透问题
   * 
   * @param id
   * 
   * @return
   */
  private Shop queryWithMutex(Long id) {
    // 先定义shop的id模版
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    // 先从redis中查询shop 以String存储
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    Shop shop;
    // 如果是空值，直接返回不存在 防止穿透
    if ("".equals(shopJson)) {
      return null;
    }
    // 如果存在且不为空值，直接返回shop对象
    if (StrUtil.isNotBlank(shopJson)) {
      shop = JSONUtil.toBean(shopJson, Shop.class);
      return shop;
    }

    // 下面解决击穿
    // 如果不存在，尝试获取锁
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    try {
      boolean flag = tryLock(lockKey);
      // 如果获取锁成功，查数据库
      if (flag) {
        shop = getById(id);
        // 从数据库中查到了，直接返回 并存入redis
        if (shop != null) {
          // 将shop对象转换为JSON字符串
          String shopStr = JSONUtil.toJsonStr(shop);
          // 将shop存入redis 设置过期时间30分钟
          stringRedisTemplate.opsForValue().set(key, shopStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
          return shop;
        }
        // 如果数据库没查到，向redis写入空值，防止缓存穿透
        stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        // 返回不存在
        return null;
      }
      // 如果获取锁失败 休眠一段时间再查redis
      Thread.sleep(50); // 休眠50毫秒
      return queryWithMutex(id); // 递归查询
    } catch (InterruptedException e) {
      throw new RuntimeException(e); // 抛出异常
    } finally {
      // 释放锁
      unlock(lockKey);
    }
  }

  /**
   * 尝试获取锁
   * 
   * @param key
   * @return
   */
  private boolean tryLock(String key) {
    // 锁有效期10s
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL,
        TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);// 返回 true表示获取锁成功，false表示获取锁失败
  }

  /**
   * 释放锁
   * 
   * @param key
   */
  private void unlock(String key) {
    stringRedisTemplate.delete(key); // 删除锁
  }

  /**
   * 更新商铺信息
   */
  @Transactional
  public Result update(Shop shop) {
    Long id = shop.getId();
    // 判断id是不是为空
    if (id == null) {
      return Result.fail("店铺id不能为空");
    }
    // 先更新数据库
    updateById(shop);
    // 再删除缓存
    stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
    return Result.ok();
  }

}

