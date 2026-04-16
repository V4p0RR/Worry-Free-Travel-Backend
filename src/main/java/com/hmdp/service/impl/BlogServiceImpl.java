package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

  @Resource
  private IUserService userService;

  @Resource
  private IBlogService blogService;

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryBlogById(Long id) {
    // 查询blog
    Blog blog = getById(id);
    if (blog == null) {
      return Result.fail("博客不存在！");
    }
    // 查询博客有关的用户
    queryBlogUser(blog);
    // 查询blog是否被点赞
    isBlogLiked(blog);
    return Result.ok(blog);
  }

  @Override
  public List<Blog> queryHotBlog(Integer current) {
    // 根据用户查询
    Page<Blog> page = query()
        .orderByDesc("liked")
        .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    // 查询用户
    records.forEach(
        blog -> {
          // 做两件事
          queryBlogUser(blog);
          isBlogLiked(blog);
        });
    return records;
  }

  /**
   * 查询博客有关用户
   * 
   * @param Blog blog
   */
  private Blog queryBlogUser(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
    return blog;
  }

  @Override
  public Result likeBlog(Long id) {
    // 获取登录用户
    if (UserHolder.getUser() == null) {
      return Result.ok();
    }
    // 利用redis zset集合 key为笔记的id 里面放点过赞的用户 分数为当前时间戳
    String key = RedisConstants.BLOG_LIKED_KEY + id.toString();
    Long userId = UserHolder.getUser().getId();
    // 用set集合 没有isMember 用zscore方法查询分数 有分数就是点过赞 没有分数就是未点过赞
    Double isLike = stringRedisTemplate.opsForZSet().score(key, userId.toString());
    if (isLike == null) {
      // 如果未点赞，则进行点赞
      boolean isSuccess = blogService.update()
          .setSql("liked = liked + 1").eq("id", id).update();
      // 保存用户到笔记的点赞集合
      if (isSuccess) {
        stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
      }
      return Result.ok();
    }
    // 如果已点赞，则取消点赞
    boolean success = blogService.update()
        .setSql("liked = liked - 1").eq("id", id).update();
    // 数据库点赞数减1 并移除点赞列表当前用户
    if (success) {
      stringRedisTemplate.opsForZSet().remove(key, userId.toString());
    }
    return Result.ok();
    // // 利用redis set集合 key为笔记的id 里面放点过赞的用户
    // String key = RedisConstants.BLOG_LIKED_KEY + id.toString();
    // Long userId = UserHolder.getUser().getId();
    // Boolean isLike = stringRedisTemplate.opsForSet().isMember(key,
    // userId.toString());

    // if (BooleanUtil.isFalse(isLike)) {
    // // 如果未点赞，则进行点赞
    // boolean isSuccess = blogService.update()
    // .setSql("liked = liked + 1").eq("id", id).update();
    // // 保存用户到笔记的点赞集合
    // if (isSuccess) {
    // stringRedisTemplate.opsForSet().add(key, userId.toString());
    // }
    // return Result.ok();
    // }
    // // 如果已点赞，则取消点赞
    // boolean success = blogService.update()
    // .setSql("liked = liked - 1").eq("id", id).update();
    // // 数据库点赞数减1 并移除点赞列表当前用户
    // if (success) {
    // stringRedisTemplate.opsForSet().remove(key, userId.toString());
    // }
    // return Result.ok();
  }

  /**
   * 判断当前用户是否已经点过赞
   * 
   * @param blog
   * @return
   */
  private void isBlogLiked(Blog blog) {
    // 获取登录用户
    if (UserHolder.getUser() == null) {
      return;
    }
    Long userId = UserHolder.getUser().getId(); // 判断当前用户是否已经点过赞
    String key = RedisConstants.BLOG_LIKED_KEY + blog.getId().toString();
    // 用sortedset 没有ismember 用zscore方法查询分数 有分数就是点过赞 没有分数就是未点过赞
    // sortedset天然按时间升序
    Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
    blog.setIsLike(isMember != null);
  }

  /**
   * 点赞排行榜
   * 
   * @param Long id
   * @return List<UserDTO>
   */
  @Override
  public List<UserDTO> getBlogLikes(Long id) {
    // 需要修改点赞集合存储方式 用sortedset 维护插入顺序
    // 查询top5 zrange key 0 4
    String key = RedisConstants.BLOG_LIKED_KEY + id.toString();
    Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
    // 判空
    if (top5 == null || top5.isEmpty()) {
      return Collections.emptyList();
    }
    // 解析用户id集合
    List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
    // 根据用户id查询用户
    // 由于IN命令不会按照传入顺序返回 要手动指定返回顺序 last:最后一条sql语句
    String idStr = StrUtil.join(",", ids);
    List<UserDTO> users = userService
        .query()
        .in("id", ids)
        .last("ORDER BY FIELD(id," + idStr + ")")
        .list()
        .stream()
        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
        .collect(Collectors.toList());
    // 返回
    return users;
  }

}
