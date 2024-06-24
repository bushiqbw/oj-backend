package com.qbw.oj.RedisAndRabbitMq;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class RedisClientTest {

    @Resource
    private RedissonClient redisClient;

    @Test
    public void RedisCreateSuccess(){
        System.out.println(redisClient);
    }
}
