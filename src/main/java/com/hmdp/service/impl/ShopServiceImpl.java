package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
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
        if ( shop == null ) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
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

    private Boolean tryLock(String key) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

//    //更新商铺信息
//    //先更新数据库再删除缓存
//    @Override
//    @Transactional
//    public Result update(Shop shop) {
//        Long id = shop.getId();
//        if ( id == null ) {
//            return Result.fail("商铺id不能为空");
//        }
//        //1. 更新数据库
//        updateById(shop);
//        //2. 更新成功，删除redis缓存
//        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
//        //3. 延时三到五秒，再次删除缓存
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return Result.ok();
//    }

    //更新商铺信息
    //延时双删
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if ( id == null ) {
            return Result.fail("商铺id不能为空");
        }
        //1. 第一次删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        //2. 更新数据库
        updateById(shop);
        //3. 开启额外线程，延时一到三秒，再次删除缓存
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
                stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
        return Result.ok();
    }

    @Override
    public Result queryByShopType(Integer typeId, Integer current, Double x, Double y) {
        //1. 判断是否根据坐标查询
        if(x == null || y == null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }
        //2. 计算分页参数
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE ;
        //3. 查询redis按照距离排序，分页，结果：shopId, distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        //4. 解析出ID
        if(results == null || results.getContent().isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        //没有下一页了
        if(content.size() <= start){
            return Result.ok(Collections.emptyList());
        }
        List<String> ids = new ArrayList<>(content.size());
        Map<String,Distance> map = new HashMap<>(content.size());
        content.stream().skip(start).forEach(geoResult -> {
            // 获取Key
            String id = geoResult.getContent().getName();
            ids.add(id);
            // 获取距离
            Distance distance = geoResult.getDistance();
            map.put(id, distance);
        });
        //5. 根据ID查询数据库商铺信息
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id",ids).last("order by field(id," + idsStr + ")").list();
        for ( Shop shop : shops ) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
