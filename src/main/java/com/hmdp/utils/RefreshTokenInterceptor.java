package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/6 下午8:31
 * @description 登录拦截器
 **/
public class RefreshTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if( StringUtils.isEmpty(token) ){
            return true;
        }
        //2. 基于token获取redis中的用户信息
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //3. 判断用户信息是否存在
        if(user.isEmpty()){
            return true;
        }
        //5. 将查询到的HashMap转换为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), false);
        //6. 如果存在，保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //7. 刷新token的过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8. 返回true，放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //释放ThreadLocal中的用户信息
        UserHolder.removeUser();
    }


}
