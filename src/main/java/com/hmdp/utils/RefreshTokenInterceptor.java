package com.hmdp.utils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hmdp.dto.UserDTO;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * * 刷新token拦截器
 * 只用于刷新token
 * 拦截请求在下一级拦截器完成
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
  // 由于拦截器不由spring管理，所以不能直接注入stringredistemplate
  // 但是mvcconfig配置类由spring管理 故在mvcconfig中注入stringredistemplate
  private StringRedisTemplate stringRedisTemplate;

  public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate; // 给拦截器注入StringRedisTemplate
  }// 添加有参构造方法 为了在mvcconfig中注入stringredistemplate

  /**
   * 检查token
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // 从请求头中获取token
    String token = request.getHeader("authorization"); // 前端在请求头中叫这个名

    // 如果token不存在 直接放行
    if (StrUtil.isBlank(token)) {
      return true;
    }
    // 如果token存在 从redis中获取用户信息
    UserDTO user = new UserDTO();
    // 从redis中获取用户信息 这个方法返回对应key的所有field
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

    if (userMap.isEmpty()) { // 如果用户信息不存在 直接放行
      return true;
    }
    // 如果用户信息存在 将Map转换为UserDTO对象
    BeanUtil.fillBeanWithMap(userMap, user, false);
    UserHolder.saveUser(user);// 用工具类UserHolder 保存用户信息到ThreadLocal中
    // 刷新token有效期
    stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL,
        TimeUnit.SECONDS);
    return true; // 放行请求
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
      @Nullable Exception ex) throws Exception {
    // 清除用户信息，防止内存泄漏
    UserHolder.removeUser(); // 清除ThreadLocal中的用户信息
  }

}
