package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //1. 缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //2. 互斥锁缓存击穿
        //Shop shop = queryWithMuTex(id);
        //3. 逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1. 从数据库中查询商铺信息
        Shop shop = getById(id);
        //模拟查询数据库耗时
        Thread.sleep(200);
        //2. 存在，写入redis
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3. 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

//    public Shop queryWithMuTex(Long id){
//        //1. 从redis中查询商铺信息
//        String key = CACHE_SHOP_KEY + id;
//        String shopJSON = stringRedisTemplate.opsForValue().get(key);
//        Shop shop;
//        //2. 判断是否存在
//        if( StrUtil.isNotBlank(shopJSON) ){
//            //3. 存在，返回商铺信息
//            shop = JSONUtil.toBean(shopJSON, Shop.class);
//            return shop;
//        }
//        //判断是否命中空值
//        if(shopJSON != null){
//            return null;
//        }
//        String lockKey = LOCK_SHOP_KEY + id;
//
//        try {
//            //4. 缓存重建
//            //4.1 尝试获取互斥锁
//            Boolean isLock = tryLock(lockKey);
//            //4.2 获取锁失败，等待重试
//            if(!isLock){
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                return queryWithMuTex(id);
//            }
//            shop = getById(id);
//            Thread.sleep(200);
//            if(shop == null){
//                //5. 不存在，将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //6. 存在，返回商铺信息并写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            //7. 释放锁
//            unlock(lockKey);
//        }
//        return shop;
//    }
//
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(4);
//
//    public Shop queryWithLogicExpire(Long id){
//        //1. 从redis中查询商铺信息
//        String key = CACHE_SHOP_KEY + id;
//        String shopJSON = stringRedisTemplate.opsForValue().get(key);
//        //2. 判断是否存在
//        if( StrUtil.isBlank(shopJSON) ){
//            //3. 查询出空值，直接返回空值
//            return null;
//        }
//        //4. 命中, json序列化转为对象
//        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //5. 判断是否逻辑过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.1 未过期，返回店铺信息
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //5.2 过期，重建缓存
//        //6.1 尝试获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Boolean isLock = tryLock(lockKey);
//        //6.2 判断是否获取成功
//        if(isLock){
//            //6.3 获取成功，开启独立线程，缓存重建
//            CACHE_REBUILD_EXECUTOR.execute(() -> {
//                try {
//                    saveShop2Redis(id, 10L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unlock(lockKey);
//                }
//            });
//        }
//        //6.4 返回过期的商铺信息
//        return shop;
//    }
//
//    public Shop queryWithPassThrough(Long id){
//        //1. 从redis中查询商铺信息
//        String key = CACHE_SHOP_KEY + id;
//        String shopJSON = stringRedisTemplate.opsForValue().get(key);
//        Shop shop;
//        //2. 判断是否存在
//        if( StrUtil.isNotBlank(shopJSON) ){
//            //3. 存在，返回商铺信息
//            shop = JSONUtil.toBean(shopJSON, Shop.class);
//            return shop;
//        }
//        //判断是否命中空值
//        if(shopJSON == null){
//            return null;
//        }
//        //4. 不存在，查询数据库
//        shop = getById(id);
//        if(shop == null){
//            //5. 不存在，将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //6. 存在，返回商铺信息并写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

    private Boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 更新成功，删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
