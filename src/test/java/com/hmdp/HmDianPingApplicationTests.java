package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        executorService.submit(() -> {
            for (int i = 0; i < 100; i++) {
                System.out.println(redisIdWorker.nextId("shop"));
            }
            countDownLatch.countDown();
        });
        long start = System.currentTimeMillis();
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("耗时：" + (System.currentTimeMillis() - start));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 15L);
    }


}
