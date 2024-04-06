package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/6 下午8:41
 * @description MVC配置
 **/
@Configuration
public class MvcConfig implements WebMvcConfigurer {
     @Override
     public void addInterceptors(InterceptorRegistry registry) {
         //添加拦截器
         registry.addInterceptor(new LoginInterceptor())
                 .excludePathPatterns(
                         "/user/login",
                         "/user/code",
                         "/blog/hot",
                         "/shop/**",
                         "/shop-type/**",
                         "/upload/**",
                         "/voucher/**"
                 );
     }
}
