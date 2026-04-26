package com.wft.controller;

import com.wft.dto.LoginFormDTO;
import com.wft.dto.Result;
import com.wft.dto.UserDTO;
import com.wft.entity.User;
import com.wft.entity.UserInfo;
import com.wft.service.IUserInfoService;
import com.wft.service.IUserService;
import com.wft.service.impl.UserServiceImpl;
import com.wft.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    // private final UserServiceImpl userServiceImpl;

    // UserController(UserServiceImpl userServiceImpl) {
    // this.userServiceImpl = userServiceImpl;
    // }

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     * 
     * @param phone   手机号
     * @param session HttpSession对象，用于存储验证码
     * @return 发送结果
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * 
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * 
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        return userService.logout(request);
    }

    /**
     * 获取当前登录的用户信息
     * 
     * @return
     */
    @GetMapping("/me")
    public Result me() {
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 查询用户详情
     * 
     * @param id 用户id
     * @return 用户详情
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.ok();
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /**
     * 用户签到
     * 
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /**
     * 统计连续签到天数
     * 
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

}
