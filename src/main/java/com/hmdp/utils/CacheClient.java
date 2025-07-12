package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.sun.xml.internal.bind.v2.model.core.ID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.service.impl.ShopServiceImpl.CACHE_REBUILD_EXECUTOR;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient (StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    //设置logicExpire的redis数据结构
    public void setWithLogicalExpire (String key,Object value,Long timeout,TimeUnit timeUnit){
        //这里timeUnit是表示expire时间的过期单位
        //设置key
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeout));
        //将redisData存储到stringRedisTemplate中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData),timeout,timeUnit);

    }
    //通过logicExpire实现处理缓存穿透的问题
    public <R,ID> R queryWithLogicExpire(String key,ID id,Class<R> type,Function<ID,R> dbFallback,Long timeout,TimeUnit unit){
        //从Redis中查询数据
        String json=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            return null;
        }
        //如果Redis中有相应的数据，查看expire获取过期时间
        //将从Redis中取出的数据反序列化为RedisData
        RedisData redisData=JSONUtil.toBean(json,RedisData.class);
        //如果该数据已经超时了，则设立一个线程单独来重建缓存
        JSONObject data=(JSONObject)redisData.getData();
        //将data反序列化成data对象
        R t=JSONUtil.toBean(data,type);
        LocalDateTime expireTime=redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return t;
        }
        //当前缓存过期了，重建缓存
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
        if(isLock){
            //开启一个线程去重建缓存
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try{
                        R t1=dbFallback.apply(id);
                        this.setWithLogicalExpire(key,t1,timeout,unit);
                    }finally{
                        unlock(lockKey);
                    };



            });
        }
        return null;





    }
    private  boolean tryLock(String key){
        //key作为是否上锁的变量，将key设置为上锁状态
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);//
        return BooleanUtil.isTrue(flag);//判断flag是否为true
    }
    //解锁的方法
    private void unlock(String key){
        //直接删除key
        stringRedisTemplate.delete(key);


    }
    //处理缓存击穿
    public <T,TD> T handleCacheBreakdown(String keyPrefix, ID id, Class<T> type,Function<ID,T> dbFallback,Long timeout,TimeUnit unit){
        //通过对空的查询进行缓存来实现处理缓存击穿
        String key=keyPrefix+id;
        String jsonStr=stringRedisTemplate.opsForValue().get(key);
        //如果缓存不为空的话，直接返回相关数据
        T t=null;
        if(jsonStr!=null){
         //将jsonStr反序化成为相关对象
            t=JSONUtil.toBean(jsonStr,type);
            return t;
        }
        //如果缓存中数据为空的话
        //1、判断查询的key是否为空

        //从数据库中查询相应信息
        t=dbFallback.apply(id);
        //如果数据库中查询到t也是空，则将这个查询加入到redis中
        if(Objects.isNull(t)){
            this.set(key,"",CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }
        this.set(key,t,timeout,unit);
        return t;




    }
    //建立一个redis对象
    private void set(String key,Object Value,Long timeout ,TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(Value),timeout,unit);
    }


    }


