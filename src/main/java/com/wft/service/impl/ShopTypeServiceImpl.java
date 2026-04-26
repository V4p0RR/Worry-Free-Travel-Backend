package com.wft.service.impl;

import com.wft.dto.Result;
import com.wft.entity.ShopType;
import com.wft.mapper.ShopTypeMapper;
import com.wft.service.IShopTypeService;
import com.wft.utils.RedisConstants;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 查询商铺类型列表
   * * 用redis缓存
   * 
   */
  @Override
  public Result queryTypeList() {
    // 定义缓存的key
    String key = RedisConstants.CACHE_SHOP_KEY + "type";
    // 先从redis中查询商铺类型列表
    String typeJson = stringRedisTemplate.opsForValue().get(key);
    // 如果存在，直接返回商铺类型列表
    if (StrUtil.isNotBlank(typeJson)) {
      // 将json转为list
      List<ShopType> shopTypes = JSONUtil.toList(typeJson, ShopType.class);
      // 返回结果
      return Result.ok(shopTypes);
    }
    // 如果不存在，查询数据库返回，并插入Redis
    List<ShopType> shopTypes = this.list(new QueryWrapper<ShopType>().orderByAsc("sort"));
    // 转为json
    typeJson = JSONUtil.toJsonStr(shopTypes);
    // 将商铺类型列表存入Redis
    stringRedisTemplate.opsForValue().set(key, typeJson);
    // 返回结果
    if (shopTypes.size() > 0) {
      return Result.ok(shopTypes);
    }
    return Result.fail("商铺类型列表不存在");
  }

}

