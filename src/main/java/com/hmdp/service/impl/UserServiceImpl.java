package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.connection.BitFieldSubCommands;
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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Resource
  private StringRedisTemplate stringRedisTemplate;// 注入 Redis 模板

  /**
   * 发送手机验证码
   * 
   * @param phone   手机号
   * @param session HttpSession对象，用于存储验证码
   */
  @Override
  public Result sendCode(String phone, HttpSession session) {
    // 校验手机号
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("手机号格式错误");
    }
    // 生成验证码
    String code = RandomUtil.randomNumbers(6);

    // 保存验证码到redis phone为key
    // 给key加业务前缀 与其他数据区分 并给code设置有效期2m
    stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL,
        TimeUnit.MINUTES);

    // 发送验证码到手机
    log.debug("success sending code: {}", code);

    // 返回结果
    return Result.ok(code);
  }

  /**
   * 登录功能
   * 
   * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
   * @param session   HttpSession对象，用于存储用户信息
   */
  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    // 先校验手机号
    if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
      return Result.fail("手机号格式错误");
    }
    // 判断验证码是否为空
    if (loginForm.getCode() != null) {
      // 如果验证码不为空 用验证码登录
      // 校验验证码
      String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
      if (cacheCode == null || !cacheCode.toString().equals(loginForm.getCode())) {
        return Result.fail("验证码错误");
      }
      // 生成token 去除UUID中的短横线
      String token = UUID.randomUUID().toString(true);

      // 检验用户是否存在数据库中
      User user = query().eq("phone", loginForm.getPhone()).one();
      if (user == null) {
        // 如果用户不存在，创建新用户
        user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)); // 随机昵称
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        // 保存用户到数据库
        save(user);
        // 保存用户信息到redis中 用hash存
        // 先将user对象转成map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        // 设置token为key，用户信息为value 加上业务前缀
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        // 设置过期时间为30分钟
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES);
        // 返回token
        log.info("login success, user: {}", BeanUtil.copyProperties(user, UserDTO.class));
        log.info("token:{}", token);
        return Result.ok(token);
      }
      // 如果用户存在,直接保存到redis中
      UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
      Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
          CopyOptions.create()
              .setIgnoreNullValue(true)
              .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));// 防止报Long无法转换为String
      stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
      // 设置过期时间为30分钟
      stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES);
      log.info("login success, user: {}", BeanUtil.copyProperties(user, UserDTO.class));
      // 返回token
      log.info("token:{}", token);
      return Result.ok(token);
      // 要在拦截器中设置只要访问了就更新token的有效期
    } else {
      // TODO 如果验证码为空，用密码登录

    }

    return Result.fail("功能未完成");
  }

  /**
   * 登出功能
   */
  @Override
  public Boolean logout(HttpServletRequest request) {
    // 获取当前登录用户
    UserDTO user = UserHolder.getUser();
    if (user != null) {
      // 从Redis中获取当前token（需要从ThreadLocal或请求中获取）
      String token = request.getHeader("authorization");
      stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
      // 清空ThreadLocal
      UserHolder.removeUser();

      return true;
    }
    return false;
  }

  /**
   * 签到功能
   */
  @Override
  public Result sign() {
    // 获取当前登录用户
    Long userId = UserHolder.getUser().getId();
    if (userId == null || userId == 0) {
      return Result.fail("用户未登录");
    }
    // 获取日期 一个用户一个月作为一个key
    LocalDateTime now = LocalDateTime.now();
    // 拼接key
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM")); // 后半部分
    String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
    // 计算offset
    int dayOfMonth = now.getDayOfMonth(); // 这里得到的是1-31 但是offset是0-30 故要-1
    // 写入redis
    stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
    return Result.ok();
  }

  /**
   * 统计签到功能
   * 从今天开始 向前连续签到的
   */
  @Override
  public Result signCount() {
    // 获取当前登录用户
    Long userId = UserHolder.getUser().getId();
    if (userId == null || userId == 0) {
      return Result.fail("用户未登录");
    }
    // 获取日期 一个用户一个月作为一个key
    LocalDateTime now = LocalDateTime.now();
    // 拼接key
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM")); // 后半部分
    String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
    // 获取今天是本月的第几天
    int dayOfMonth = now.getDayOfMonth();
    int count = 0;
    // 获取本月到今天的签到记录 十进制数字
    List<Long> list = stringRedisTemplate.opsForValue().bitField(
        key, BitFieldSubCommands.create()
            .get(BitFieldSubCommands.BitFieldType
                .unsigned(dayOfMonth))
            .valueAt(0));
    if (list == null || list.isEmpty()) {
      return Result.ok(0);
    }
    Long sign = list.get(0);
    if (sign == null || sign == 0) {
      return Result.ok(0);
    }
    // 循环遍历 与1与运算
    for (int i = 0; i < dayOfMonth; i++) {
      // 判断是否签到
      if ((sign & 1) == 0) {
        break;
      } else {
        count++;
      }
      sign >>>= 1;
    }
    return Result.ok(count);
  }

}
