package com.wft.config;

import javax.annotation.Resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.wft.utils.LoginInterceptor;
import com.wft.utils.RefreshTokenInterceptor;

/**
 * MVC配置类
 * 注册拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
        "/user/code",
        "/user/login",
        "/user/logout",
        "/user/{id}",
        "/user/info/{id}",
        "/blog/hot",
        "/blog/{id}",
        "/blog/likes/{id}",
        "/blog/of/user",
        "/shop/**",
        "/shop-type/**",
        "/voucher/list/{shopId}",
        "/upload/**").order(1);
    registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);// 默认拦截所有请求 调节顺序，第一个执行
  }

}
