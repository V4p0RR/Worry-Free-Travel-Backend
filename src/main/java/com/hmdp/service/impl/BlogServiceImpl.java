package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.util.BooleanUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    // 利用redis set集合 key为笔记的id 里面放点过赞的用户
    String key = "blog:liked:" + id.toString();
    Long userId = UserHolder.getUser().getId();
    Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

    if (BooleanUtil.isFalse(isLike)) {
      // 如果未点赞，则进行点赞
      boolean isSuccess = blogService.update()
          .setSql("liked = liked + 1").eq("id", id).update();
      // 保存用户到笔记的点赞集合
      if (isSuccess) {
        stringRedisTemplate.opsForSet().add(key, userId.toString());
      }
      return Result.ok();
    }
    // 如果已点赞，则取消点赞
    boolean success = blogService.update()
        .setSql("liked = liked - 1").eq("id", id).update();
    // 数据库点赞数减1 并移除点赞列表当前用户
    if (success) {
      stringRedisTemplate.opsForSet().remove(key, userId.toString());
    }
    return Result.ok();
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
    String key = "blog:liked:" + blog.getId().toString();
    Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
    blog.setIsLike(BooleanUtil.isTrue(isMember));
  }

}
