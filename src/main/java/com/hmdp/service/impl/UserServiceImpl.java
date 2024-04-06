package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

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
        //4. 将验证码保存到 session 中
        session.setAttribute("code", code);
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
        //2. 校验验证码
        Object CacheCode = session.getAttribute("code");
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
        //7. 将用户信息保存到 session 中
        log.info("用户登录成功，用户信息：{}", user);
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(user);
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
