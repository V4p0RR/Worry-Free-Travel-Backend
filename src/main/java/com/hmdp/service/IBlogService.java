package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

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
public interface IBlogService extends IService<Blog> {

  /**
   * 根据id查询博客
   * 
   * @param id
   * @return
   */
  Result queryBlogById(Long id);

  /**
   * 查询热搜博客
   * 
   * @param current
   * @return
   */
  List<Blog> queryHotBlog(Integer current);

  /**
   * 点赞
   * 
   * @param Long id
   */
  Result likeBlog(Long id);
}
