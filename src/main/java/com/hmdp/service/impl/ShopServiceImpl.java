package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
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

    public Result queryById(Long id) {
        // 1.实现逻辑
        // 1.1.查询缓存
        log.info("查询店铺缓存");
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 1.2.判断缓存是否存在
        if (shopJson != null) {
            // 1.3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 1.4.不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), TimeUnit.MINUTES.toSeconds(30));
        return Result.ok(shop);
    }


}
