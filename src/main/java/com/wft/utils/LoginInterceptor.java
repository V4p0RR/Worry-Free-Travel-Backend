package com.wft.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器，用于检查用户是否登录
 * 实现HandlerInterceptor接口
 */
public class LoginInterceptor implements HandlerInterceptor {
  // 由于拦截器不由spring管理，所以不能直接注入stringredistemplate
  // 但是mvcconfig配置类由spring管理 故在mvcconfig中注入stringredistemplate

  /**
   * 拦截请求，检查用户是否登录
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // 这里只需要判断是否需要拦截
    // 如果线程中不存在用户，则拦截
    if (UserHolder.getUser() == null) {
      response.setStatus(401);
      return false;
    }
    // 如果存在，则放行
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
      @Nullable Exception ex) throws Exception {
    // 清除用户信息，防止内存泄漏
    UserHolder.removeUser(); // 清除ThreadLocal中的用户信息
  }

}

