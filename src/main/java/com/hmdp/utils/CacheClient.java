package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/8 上午11:49
 * @description 缓存客户端
 **/
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value,Long time, TimeUnit timeUnit) {
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String prefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        //1. 从redis中查询商铺信息
        String key = prefix + id;
        String objJSON = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if( StrUtil.isNotBlank(objJSON) ){
            //3. 存在，返回查询结果
            return JSONUtil.toBean(objJSON, type);
        }
        //判断是否命中空值
        if(objJSON != null){
            return null;
        }
        //4. 不存在，查询数据库
        R obj = dbFallback.apply(id);
        if(obj == null){
            //5. 不存在，将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6. 存在，返回查询结果并写入redis
        this.set(key, obj, time, timeUnit);
        return obj;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(12);

    public <R,ID> R queryWithLogicExpire(
            String prefix,ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        //1. 从redis中查询物品信息
        String key = prefix + id;
        String objJSON = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if( StrUtil.isBlank(objJSON) ){
            //3. 查询出空值，直接返回空值
            return null;
        }
        //4. 命中, json序列化转为对象
        RedisData redisData = JSONUtil.toBean((JSONObject) JSONUtil.parse(objJSON), RedisData.class);
        R obj = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5. 判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.1 未过期，返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return obj;
        }
        //5.2 过期，重建缓存
        //6.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取成功
        if(isLock){
            //6.3 获取成功，开启独立线程，缓存重建
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    R r = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //6.4 返回过期的商铺信息
        return obj;
    }

    private Boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
