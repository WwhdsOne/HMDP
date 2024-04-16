package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result likeBlog(Long id) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if ( score == null ) {
            //3. 如果没有点赞过，修改点赞数量
            //3.1 保存到数据库
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if ( isSuccess ) {
                //3.2 保存到redis中的,sortedSet
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //4. 如果点赞过了，取消点赞
            //4.1 数据库点赞数量-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if ( isSuccess ) {
                //4.2 redis删除点赞记录
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1. 查询Top5点赞用户 zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2. 解析其中的用户ID
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3. 根据ID查询用户信息，变为UserDTO集合
        List<UserDTO> userDTOS = userService.query().in("id", ids).
                last("order by field(id," + StrUtil.join(",", top5) + ")").list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2. 保存到数据库
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("发布博客失败");
        }
        //3. 查询博客作者粉丝
        followService.query().eq("follow_user_id", user.getId()).list().forEach(follow -> {
            //4.1 获取粉丝id
            Long fansId = follow.getUserId();
            String key = "feed:" + fansId;
            //4.2 保存到redis中的sortedSet
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        //5. 返回结果
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        //2. 查询收件箱
        String key = "feed:" + user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        int os = 1;
        long minTime = 0;
        //3. 解析收件箱blogId/minTime/offset
        for ( ZSetOperations.TypedTuple<String> typedTuple : typedTuples ) {
            //3.1 获取blogId
            ids.add(Long.valueOf(typedTuple.getValue()));
            //3.2 获取时间
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                os = 1;
                minTime = time;
            }
        }
        //4. 根据blogId查询blog
        List<Blog> blogs = query().in("id", ids).
                last("order by field(id," + StrUtil.join(",", ids) + ")").list();
        //5. 封装返回结果
        for ( Blog blog : blogs ) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        System.out.println(records);
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }


    @Override
    public Result queryBlogById(Long id) {
        //1. 查询blog
        Blog blog = getById(id);
        if ( blog == null ) {
            return Result.fail("博客不存在");
        }
        //2. 查询blog有关用户
        queryBlogUser(blog);
        //3. 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if ( user == null ) {
            //用户未登录无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
