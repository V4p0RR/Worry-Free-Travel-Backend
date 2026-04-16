package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
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
