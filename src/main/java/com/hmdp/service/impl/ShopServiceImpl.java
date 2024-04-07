package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    @Override
    public Result queryById(Long id) {
        //1. 从redis中查询商铺信息
        String shopJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        Shop shop;
        //2. 判断是否存在
        if(shopJSON != null){
            //3. 存在，返回商铺信息
            shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        //4. 不存在，查询数据库
        shop = getById(id);
        if(shop == null){
            //5. 不存在，返回错误
            return Result.fail("商铺不存在");
        }
        //6. 存在，返回商铺信息并写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
