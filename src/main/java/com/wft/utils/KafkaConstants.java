package com.wft.utils;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaConstants {
  // Kafka 消费者配置
  public static final Properties KAFKA_CONSUMER_CONFIG;
  static {
    KAFKA_CONSUMER_CONFIG = new Properties();
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.GROUP_ID_CONFIG, "seckill-order-group");
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    // 手动提交 offset
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    // 每次最多拉多少条
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");
    // 没有初始 offset 时从最早开始消费，防止重启丢消息
    KAFKA_CONSUMER_CONFIG.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
  }

  // Kafka 生产者配置
  public static final Properties KAFKA_PRODUCER_CONFIG;
  static {
    KAFKA_PRODUCER_CONFIG = new Properties();
    KAFKA_PRODUCER_CONFIG.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    KAFKA_PRODUCER_CONFIG.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    KAFKA_PRODUCER_CONFIG.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    // 最强可靠性：所有副本确认才算成功
    KAFKA_PRODUCER_CONFIG.put(ProducerConfig.ACKS_CONFIG, "all");
    // 幂等生产者，防止网络重试导致重复消息
    KAFKA_PRODUCER_CONFIG.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
  }

}

