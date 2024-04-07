package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        //2. 不符合要求，返回错误信息
        if ( phoneInvalid ) {
            return Result.fail("手机号格式不正确");
        }
        //3. 符合要求，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 将验证码保存到 redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5. 发送短信验证码
        System.out.println("发送短信验证码：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号
        String phone = loginForm.getPhone();
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if ( phoneInvalid ) {
            return Result.fail("手机号格式不正确");
        }
        //2. 从redis获取验证码并校验
        String CacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if ( CacheCode == null || !CacheCode.toString().equals(code) ) {
            //3. 验证码错误，返回错误信息
            return Result.fail("验证码错误");
        }
        //4. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5. 判断用户是否存在
        if ( user == null ) {
            //6. 不存在，创建用户并保存
            user = createUserWithPhone(phone);
        }
        //7. 将用户信息保存到 redis 中
        //7.1 随机生成一个 token作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //7.2 将user对象转为HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //7.3 将token和hash保存到redis中
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((value, prop) -> prop.toString())
        );
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap );
        //7.4 设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        //2. 保存用户
        save(user);
        return user;
    }
}
