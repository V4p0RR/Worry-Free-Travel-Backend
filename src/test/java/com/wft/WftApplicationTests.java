package com.wft;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wft.controller.UserController;
import com.wft.dto.LoginFormDTO;
import com.wft.dto.Result;
import com.wft.entity.User;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
class WftApplicationTests {
  @Resource
  private UserController userController;
  @Resource
  private IService<User> userService;
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 引入用户 用于测试并发
   */
  @Test
  public void loadUser() {
    for (int i = 0; i < 3500; i++) {
      User user = userService.getById(i);
      if (user == null) {
        continue;
      }
      String code = (String) userController.sendCode(user.getPhone(), null).getData();
      LoginFormDTO loginFormDTO = new LoginFormDTO();
      loginFormDTO.setPhone(user.getPhone());
      loginFormDTO.setCode(code);
      Result loginResult = userController.login(loginFormDTO, null);
      log.info("用户id:{},token:{}", i, loginResult.getData());
    }
    log.info("所有用户加载完毕");
  }

  /**
   * 导出 Redis 中的 token
   * 
   * @throws IOException
   */
  @Test
  void exportTokens() throws IOException {
    // 匹配所有 token key（注意修改前缀为你项目里 RedisConstants.LOGIN_USER_KEY）
    Set<String> keys = stringRedisTemplate.keys("login:token:*");

    try (FileWriter writer = new FileWriter("tokens.txt")) {
      for (String key : keys) {
        // 提取 token 部分
        String token = key.replace("login:token:", "");
        writer.write(token + "\n");
      }
    }
    System.out.println("导出完成，总数：" + keys.size());
  }

}

