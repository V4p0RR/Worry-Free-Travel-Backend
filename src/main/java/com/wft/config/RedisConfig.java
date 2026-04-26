package com.wft.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 */
@Configuration
public class RedisConfig {
  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    // 添加单点redis地址
    config.useSingleServer()
        .setAddress("redis://127.0.0.1:6379")
        .setPassword("123456");

    // 创建客户端
    return Redisson.create(config);
  }
}
