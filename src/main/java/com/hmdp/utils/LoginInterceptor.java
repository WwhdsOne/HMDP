package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/6 下午8:31
 * @description 登录拦截器
 **/
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //1. 获取session
        HttpSession session = request.getSession();
        //2. 获取session中的用户信息
        Object user = session.getAttribute("user");
        //3. 判断用户信息是否存在
        if(user == null){
            //4. 如果不存在，拦截
            response.setStatus(401);
            return false;
        }
        //5. 如果存在，保存到ThreadLocal中
        UserHolder.saveUser((UserDTO) user);
        //6. 返回true，放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //释放ThreadLocal中的用户信息
        UserHolder.removeUser();
    }


}
