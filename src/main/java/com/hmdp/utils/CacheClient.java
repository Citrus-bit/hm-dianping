package com.hmdp.utils;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.hmdp.entity.Shop;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 写入缓存
    public void set(String key, Object value, Long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    // 写入缓存并设置过期时间
    public void setWithLogicalExpire(String key, Object value, Long timeout, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        // 写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T queryWithPassThrough(
        String keyPrefix, ID id, Class<T> clazz, Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //判断商铺是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在，直接返回
            T t = JSONUtil.toBean(json, clazz);
            return t;
        }


        // 判断是否为空值
        if (json != null) {
            // 是，返回错误信息
            return null;
        }
        
        //不存在，查询数据库
        T t = dbFallback.apply(id);
        //判断商铺是否存在
        if (t == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(keyPrefix + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }

        //存在，写入redis
        this.set(keyPrefix + id, t, timeout, unit);
        
        //返回商铺详情数据
        return t;
    }



    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 逻辑过期解决缓存击穿
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //判断商铺是否存在
        if (StrUtil.isBlank(json)) {
            //不存在，直接返回
            return null;
        }

        
        //命中，需要判断过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return t;
        }

        //过期，需要缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //获取锁成功，开启独立线程，查询数据库
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    T t1 = dbFallback.apply(id);
                    //存在，写入redis
                    this.setWithLogicalExpire(keyPrefix + id, t1, timeout, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放互斥锁
                    unlock(lockKey);
                }
            });
        }
        return t;
    }

    // 尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    
}


