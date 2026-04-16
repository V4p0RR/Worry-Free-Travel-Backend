package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 关注或取消关注
   * 
   * @param id       关注的用户id
   * @param isFollow 是否关注
   * @return
   */
  @Override
  public Result follow(Long id, Boolean isFollow) {
    // 获取当前登录用户
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      return Result.fail("用户未登录");
    }
    // 判断是关注还是取关
    String key = "follows:" + user.getId();
    if (isFollow) {
      // 关注
      Follow follow = new Follow();
      follow.setUserId(user.getId());
      follow.setFollowUserId(id);
      // 保存数据
      boolean isSuccess = save(follow);
      if (isSuccess) {
        // 存入redis set key为当前用户id value为被关注用户id
        stringRedisTemplate.opsForSet().add(key, id.toString());
        return Result.ok();
      }
      return Result.fail("关注失败");
    }
    // 取关
    boolean isSuccess = remove(new QueryWrapper<Follow>()
        .eq("user_id", user.getId())
        .eq("follow_user_id", id));
    if (isSuccess) {
      // 从redis中删除数据
      stringRedisTemplate.opsForSet().remove(key, id.toString());
      return Result.ok();
    }
    return Result.fail("取关失败");
  }

  /**
   * 查询是否关注
   * 
   * @param id 关注的用户id
   * @return
   */
  @Override
  public Result isFollow(Long id) {
    // 获取当前登录用户
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      return Result.fail("用户未登录");
    }
    boolean isFollow = query().eq("user_id", user.getId())
        .eq("follow_user_id", id).count() > 0;
    return Result.ok(isFollow);
  }

}
