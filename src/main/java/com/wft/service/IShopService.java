package com.wft.service;

import com.wft.dto.Result;
import com.wft.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

  /**
   * 根据id查询商铺信息
   * 用redis缓存
   * 
   * @param id
   * @return
   */
  Result queryShopById(Long id);

  /**
   * 更新商铺信息
   * 用redis
   *
   * @param shop
   * @return
   */
  Result update(Shop shop);
}

