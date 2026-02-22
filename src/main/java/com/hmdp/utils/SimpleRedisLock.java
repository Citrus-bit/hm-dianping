package com.hmdp.utils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import cn.hutool.core.lang.UUID;

public class SimpleRedisLock implements ILock {
    // 锁的名称
    private String lockName;
    private StringRedisTemplate redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    // lua脚本释放锁
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("luaScript.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    
    
    public SimpleRedisLock(String lockName, StringRedisTemplate redisTemplate) {
        this.lockName = lockName;
        this.redisTemplate = redisTemplate;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 1. 尝试获取锁
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + lockName, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }


    
    // 调用lua脚本释放锁
    @Override
    public void unlock() {
        // 2. 执行lua脚本
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + lockName),
                ID_PREFIX + Thread.currentThread().getId());
    }


    // 2. 释放锁
    /* @Override
    public void unlock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 1. 获取锁中的线程标识
        String id = redisTemplate.opsForValue().get(LOCK_PREFIX + lockName);
        // 2. 判断是否与当前线程标识一致
        if (threadId.equals(id)) {
            // 3. 一致，释放锁
            redisTemplate.delete(LOCK_PREFIX + lockName);
        }
        
    } */

    

    
}
