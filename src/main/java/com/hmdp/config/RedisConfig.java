package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/14 下午8:42
 * @description redisson配置
 **/
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config = new Config();
        config.useSingleServer().setAddress("redis://47.93.83.136:6379").setPassword("Wwh852456");
        //创建实例
        return Redisson.create(config);
    }
}
