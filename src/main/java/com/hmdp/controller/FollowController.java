package com.hmdp.controller;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

  @Resource
  private IFollowService followService;

  /**
   * 关注或取关
   * 
   * @param id       关注的用户id
   * @param isFollow true 关注 false 取关
   */
  @PutMapping("/{id}/{isFollow}")
  public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow) {
    return followService.follow(id, isFollow);
  }

  /**
   * 查询是否关注
   * 
   * @param id 关注的用户id
   */
  @GetMapping("/or/not/{id}")
  public Result isFollow(@PathVariable Long id) {
    return followService.isFollow(id);
  }

  /**
   * 查询共同关注
   * 
   */
  @GetMapping("/common/{id}")
  public Result followCommons(@PathVariable Long id) {
    return Result.ok(followService.followCommons(id));
  }

}
