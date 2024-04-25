package com.hmdp;

import com.hmdp.componet.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RabbitMQTests {

    @Autowired
    Producer producer;
    @Test
    public void amqpTest() throws InterruptedException {
        // 生产者发送消息
        producer.produce();
        // 让子弹飞一会儿
        Thread.sleep(1000);
    }
}
