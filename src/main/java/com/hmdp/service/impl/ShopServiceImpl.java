package com.hmdp.service.impl;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Result queryWithPassthrough(Long id) {
        // 1.实现逻辑
        // 1.1.查询缓存
        log.info("查询店铺缓存");
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 1.2.判断缓存是否存在
        // if (shopJson != null) {
        // // 1.3.存在，直接返回
        // Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        // return Result.ok(shop);
        // }
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return Result.fail("店铺不存在0");
        }
        // 1.4.不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 将空对象写入缓存
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),
                TimeUnit.MINUTES.toSeconds(CACHE_SHOP_TTL));
        return Result.ok(shop);
    }

    private boolean trylock(String key) {
        // 获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 判断获取锁成功
        if (flag) {
            return true;
        }
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 将shop封装到逻辑过期时间中加入Redis
    public void saveShop2Redis(Long id, Long ttl) {
        Shop shop = getById(id);
        // 封装shop对象到Redis对象中
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        // 将Redis对象放进redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result update(Shop shop) {
        // 更新数据库
        log.info("更新店铺缓存");
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }

    // 通过互斥锁防止缓存穿透
    public Shop queryWithMutex(Long id) {
        log.info("查询店铺缓存");
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 1.2.判断缓存是否存在
        // if (shopJson != null) {
        // // 1.3.存在，直接返回
        // Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        // return Result.ok(shop);
        // }
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return null;
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        // 1.4.不存在，根据id查询数据库
        String lockKey = "lock:shop" + id;
        boolean isLock = trylock(lockKey);
        if (!isLock) {
            // 获取锁失败，则休眠并重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
            return queryWithMutex(id);
        }
        Shop shop = getById(id);
        if (shop == null) {
            // 将空对象写入缓存
            // stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", CACHE_SHOP_TTL,
            // TimeUnit.MINUTES);
            // unlock(lockKey);
            // return null;//缓存和数据库都没有命中
            // 开启一个线程，实现将空对象加入缓存的功能
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });

            return shop;
        }
        // 释放互斥锁
        unlock(lockKey);
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),
                TimeUnit.MINUTES.toSeconds(CACHE_SHOP_TTL));
        return shop;
    }

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicExpire(Long id) {
        log.info("查询店铺缓存");
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);

        // 如果缓存未命中，直接返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 反序列化为RedisData
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return shop;
        }

        // 已过期，需要缓存重建
        String lockKey = "lock:shop:" + id;
        boolean isLock = trylock(lockKey);
        if (isLock) {
            // 获取锁成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 返回过期数据
        return shop;
    }

}
