package com.hmdp.service.impl;

import com.hmdp.config.RedisConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
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

  private final RedisConfig redisConfig;

  @Resource
  private IUserService userService;

  @Resource
  private IBlogService blogService;

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private IFollowService followService;

  BlogServiceImpl(RedisConfig redisConfig) {
    this.redisConfig = redisConfig;
  }

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

  /**
   * 保存笔记
   * 
   * @param blog
   * @return Result
   */
  @Override
  public Result saveBlog(Blog blog) {
    if (blog.getShopId() == null) {
      return Result.fail("请选择店铺！");
    }
    if (blog.getContent() == null) {
      return Result.fail("请填写内容！");
    }
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    // 保存探店博文
    boolean isSuccess = blogService.save(blog);
    if (!isSuccess) {
      return Result.fail("笔记保存失败！");
    }
    // 查询所有粉丝 select * from tb_follow where follow_id = ?
    List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
    // 推送给所有粉丝
    for (Follow follow : follows) { // 每个用户都有一个zset收件箱
      String key = RedisConstants.FEED_KEY + follow.getUserId();
      stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
    }
    // 返回id
    return Result.ok(blog.getId());
  }

  @Override
  public Result queryBlogOfFollow(Long max, Integer offset) {
    // 获取当前用户收件箱
    Long userId = UserHolder.getUser().getId();
    // 解析数据 blogId,minTime,offset
    String key = RedisConstants.FEED_KEY + userId;
    // ZREVRANGEBYSCORE key Max Min LIMIT offset cOunt
    Set<TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
        .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

    // 判断数据是否为空
    if (tuples == null || tuples.isEmpty()) {
      return Result.ok();
    }
    long minTime = 0;
    int os = 1;// 最小时间重复的个数
    List<Long> ids = new ArrayList<>(tuples.size());
    for (TypedTuple<String> tuple : tuples) {
      ids.add(Long.valueOf(tuple.getValue()));
      Long time = tuple.getScore().longValue(); // 最后一个必为mintime
      if (minTime == time) {
        os++;
      } else {
        minTime = time;
        os = 1;
      }
    }
    // 根据id查blog
    String idStr = StrUtil.join(",", ids);
    List<Blog> blogs = blogService.query()
        .in("id", ids)
        .last("ORDER BY FIELD(id," + idStr + ")")
        .list();
    for (Blog blog : blogs) {
      // 查询博客有关的用户
      queryBlogUser(blog);
      // 查询blog是否被点赞
      isBlogLiked(blog);
    }
    // 封装 返回
    ScrollResult scrollResult = new ScrollResult();
    scrollResult.setList(blogs);
    scrollResult.setMinTime(minTime);
    scrollResult.setOffset(os);
    return Result.ok(scrollResult);
  }

}
