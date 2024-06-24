package com.qbw.oj.RabbitMq;


import com.qbw.oj.constant.MqConstant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


@SpringBootTest
public class RabbitMqTest {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testSendMessage(){
        rabbitTemplate.convertAndSend(MqConstant.DIRECT_EXCHANGE, "oj", "你好");
    }

    @Test
    public void testConnectionMessagg(){

        rabbitTemplate.convertAndSend("simple.queue","hello");
    }
}
