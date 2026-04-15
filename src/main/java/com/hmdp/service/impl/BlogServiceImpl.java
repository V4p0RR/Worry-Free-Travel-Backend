package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import javax.annotation.Resource;

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

  @Override
  public Result queryBlogById(Long id) {
    // 查询blog
    Blog blog = getById(id);
    if (blog == null) {
      return Result.fail("博客不存在！");
    }
    // 查询博客有关的用户
    queryBlogUser(blog);
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
    records.forEach(blog -> {
      queryBlogUser(blog);
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
}
