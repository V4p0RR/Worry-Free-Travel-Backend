package com.wft.service;

import com.wft.dto.Result;
import com.wft.dto.UserDTO;
import com.wft.entity.Follow;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

  /**
   * 关注或取关
   * 
   * @param id
   * @param isFollow
   * @return
   */
  Result follow(Long id, Boolean isFollow);

  /**
   * 判断是否关注
   * 
   * @param id
   * @return
   */
  Result isFollow(Long id);

  /**
   * 共同关注
   * 
   * @param id
   * @return List<UserDTO>
   */
  List<UserDTO> followCommons(Long id);

}

