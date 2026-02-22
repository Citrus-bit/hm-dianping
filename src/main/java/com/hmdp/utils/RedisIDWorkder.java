package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class RedisIDWorkder {
    // 初始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    

    // keyPrefix可以理解成业务的前缀
    public Long nextId(String keyPrefix) {
        // 生成时间戳
        long timestamp = System.currentTimeMillis() - BEGIN_TIMESTAMP;
        // 生成序列数
        // 获取当前日期进行自增长，实现每天都有一个新的序列数
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long sequence = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        
        // 拼接返回
        return (timestamp << COUNT_BITS) | sequence;
    }
    
}
