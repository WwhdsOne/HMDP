package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

    @Test
    void loadShopData(){
        //1. 从数据库中查询商铺信息
        List<Shop> list = shopService.list();
        //2. 根据种类分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3. 分批存入redis
        map.forEach((k, v) -> {
            //1. 获取类型ID
            Long typeId = k;
            String key = SHOP_GEO_KEY + k;
            //2. 获取同类型店铺集合
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for(Shop shop : v){
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        });
    }

    @Test
    void testHyperloglog(){
        String [] values = new String[1000];
        for (int i = 0; i < 1000000; i++) {
            int j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }

//    @Test
//    void rabbitmqSend() throws IOException {
//        RabbitMQTest.send();
//    }
//
//    @Test
//    void rabbitmqReceiveBoth() throws IOException {
//        ExecutorService executorService = Executors.newFixedThreadPool(2);
//
//        executorService.submit(() -> {
//            try {
//                RabbitMQTest.receiveOne();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//
//        executorService.submit(() -> {
//            try {
//                RabbitMQTest.receiveTwo();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//        try {
//            // 阻塞当前线程，直到所有任务都已完成执行，或者超出了指定的等待时间
//            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
}
