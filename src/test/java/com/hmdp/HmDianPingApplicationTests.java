package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;
    //对shopServiceImpl方法进行测试
    @Resource
    private ShopServiceImpl shopService;
    @Test
    void testSaveShop() throws InterruptedException{
        //先声明一个shop类
        Shop shop=shopService.getById(10L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+10L,shop,10L, TimeUnit.SECONDS);
    }



}
