package com.wft;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.wft.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class WftApplication {
    public static void main(String[] args) {
        SpringApplication.run(WftApplication.class, args);
    }

}
