package com.wft.service;

import com.wft.dto.Result;
import com.wft.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {
  /**
   * 查询商铺类型列表
   * 用redis缓存
   *
   * @return
   */
  Result queryTypeList();
}

