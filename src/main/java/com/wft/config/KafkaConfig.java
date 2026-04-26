package com.wft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wft.utils.KafkaConstants;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.CreateTopicsResult;

import java.util.Collections;

import javax.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
/**
 * Kafka配置类
 * 初始化KafkaTopic和KafkaProducer
 */
public class KafkaConfig {

  // topic名, partition数（建议3/6/10）, 副本数
  // 经验公式：分区数 = 消费者实例数 × 每个实例的拉取线程数。对于单机压测，设为 6 或 10 比较合适。
  // 本地压测副本设为1性能最好
  @Bean
  public AdminClient adminClient() {
    return AdminClient.create(KafkaConstants.KAFKA_PRODUCER_CONFIG);
  }

  // 启动时自动执行
  @PostConstruct
  public void createOrAlterTopic() {
    try (AdminClient admin = adminClient()) {
      // 1. 获取现有 Topic 信息
      boolean exists = admin.listTopics().names().get().contains("voucher-order");

      if (!exists) {
        // 如果不存在，创建 10 分区的
        NewTopic newTopic = new NewTopic("voucher-order", 10, (short) 1);
        admin.createTopics(Collections.singletonList(newTopic)).all().get();
        log.info("Kafka Topic 'voucher-order' 创建成功，分区数：10");
      } else {
        // 如果已存在且分区不对，可以在这里 alter，或者简单的手动删了让它重建
        log.info("Topic 'voucher-order' 已存在，请确认分区数是否为 10");
      }
    } catch (Exception e) {
      log.error("初始化 Kafka Topic 失败", e);
    }
  }
}
