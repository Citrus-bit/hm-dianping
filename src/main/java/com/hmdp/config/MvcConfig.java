package com.hmdp.config;

import javax.annotation.Resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hmdp.utils.LoginIntercepter;
import com.hmdp.utils.RefreshTokenIntercepter;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加登录拦截器
        registry.addInterceptor(new LoginIntercepter())
                //添加拦截路径
                .addPathPatterns("/**")
                //添加排除路径
                .excludePathPatterns(
                    "/user/login",
                    "/user/code",
                    "/shop/**",
                    "/shop-type/**",
                    "/voucher/**",
                    "/voucher-order/**",
                    "/blog/hot",
                    "/upload/**"
                ).order(1);
        
        //添加刷新token拦截器
        registry.addInterceptor(new RefreshTokenIntercepter(stringRedisTemplate)).order(0);
        
    }
    
}
