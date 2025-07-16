package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import java.util.UUID;

public class SimpleRedisLock implements Ilock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<Long>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识,并和UUID一起构成唯一识别码
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        // 将锁存入redis中
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadID + "", timeoutSec,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock() {
        // 将redis中的锁取出来对比线程标识和UUID
        // String threadID=ID_PREFIX+Thread.currentThread().getId();
        // String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
        // if(threadID.equals(id)){
        // stringRedisTemplate.delete(KEY_PREFIX+name);
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()

        );

    }
}
