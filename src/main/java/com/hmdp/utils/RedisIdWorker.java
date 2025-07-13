package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP=1630995200;
    private static final int COUNT_BITS=32;

    //生成全局id
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        //生成自增id
        long count=stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        //序列号=时间戳+自增id
        return timestamp<<COUNT_BITS|count;
    }
}
