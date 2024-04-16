package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1. 获取用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //2. 判断是否关注
        if ( isFollow ) {
            //3. 数据库新增关注数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if ( isSuccess ) {
                //把关注用户的id放入redis中的set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //4. 数据库删除关注数据
            LambdaQueryWrapper<Follow> query = new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            boolean isSuccess = remove(query);
            if ( isSuccess ) {
                //把关注用户的id从redis中的set集合中删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1. 查询是否关注过
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> query = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId);
        Follow follow = getOne(query);
        //2. 返回是否关注
        return Result.ok(follow != null);
    }

    @Override
    public Result followCommons(Long id) {
        //1. 获取用户ID
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //2. 获取两个用户的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if ( intersect == null || intersect.isEmpty() ) {
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3. 解析出ID
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4. 查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
