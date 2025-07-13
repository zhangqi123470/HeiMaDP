package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Resource
    private RedisIdWorker redisIdWorker;
    @Test
    void testIdWorer(){
        //建立一个线程池来执行方法
        CountDownLatch latch=new CountDownLatch(300);
        Runnable task=()->{
            //声明一个实例来执行多次redisIdworker方法
            try{
                for(int i=0;i<100;i++){
                    long id=redisIdWorker.nextId("order");
                    System.out.println("id="+id);
                    //每个线程执行100次id生成
                }
            }finally{
                latch.countDown();
            }



        };
        long begin =System.currentTimeMillis();
        //向系统提交线程
        for(int i=0;i<300;i++){
            es.submit(task);
        }
        //关闭线程
        try{
            latch.await();
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        long end=System.currentTimeMillis();
        //计算线程执行时间
        System.out.println("生成3w个id一共耗时:"+(end-begin)+"ms");

    }



}
