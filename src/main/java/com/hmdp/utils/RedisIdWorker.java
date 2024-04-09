package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/9 下午7:16
 * @description RedisId生成器
 **/
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    /**
     * 序列号位数
     */
    private static final long COUNT_BITS = 32L;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    public Long nextId(String keyPrefix){
        //1. 生成时间戳 31位
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        //2. 生成序列号 32位
        //2.1 获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //2.2 获取序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3. 拼接
        return timestamp << COUNT_BITS | count;
    }

}
