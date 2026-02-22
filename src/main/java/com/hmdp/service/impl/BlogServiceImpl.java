package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.util.BooleanUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import javax.annotation.Resource;
import com.hmdp.utils.RedisConstants;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 如果未点赞，就数据库点赞数+1，保存到redis
        Boolean isLike = redisTemplate.opsForValue().getOperations().hasKey(RedisConstants.BLOG_LIKED_KEY + id);
        if (BooleanUtil.isFalse(isLike)){
            // 未点赞，点赞数+1
            this.update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            // 保存用户到redis
            redisTemplate.opsForValue().set(RedisConstants.BLOG_LIKED_KEY + id, userId);
        }
        
        // 已点赞，取消点赞，点赞数-1，把用户从redis中移除
        else{
            this.update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            // 移除用户
            redisTemplate.opsForValue().getOperations().delete(RedisConstants.BLOG_LIKED_KEY + id);
        }
             
        
        // 返回结果
        return Result.ok();
    }
}
