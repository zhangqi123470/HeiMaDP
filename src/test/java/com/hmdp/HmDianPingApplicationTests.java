package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    IUserService userService;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Test
    public void testGetAll() {


        List<User> users = userService.list();
//        for(User user : users){


//            System.out.println(user);
//        }
//        System.exit(0);
        users.forEach(
                user -> {


                    //          7.1,随机生成token,作为登录令牌
                    String token = UUID.randomUUID().toString(true);
//        7.2,将User对象转化为HashMap存储
                    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                    File file = new File("E:\\黑马点评资料\\hm-dianping\\hm-dianping\\token.txt");
                    FileOutputStream output = null;
                    try {


                        output = new FileOutputStream(file, true);
                        byte[] bytes = token.getBytes();
                        output.write(bytes);
                        output.write("\r\n".getBytes());
                    } catch (Exception e) {


                        throw new RuntimeException(e);
                    } finally {


                        try {


                            output.close();
                        } catch (IOException e) {


                            throw new RuntimeException(e);
                        }
                    }
                    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                            CopyOptions.create()
                                    .setIgnoreNullValue(true)
                                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//        7.3,存储
                    String tokenKey = LOGIN_USER_KEY + token;
                    stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        7.4,设置token有效期
                    stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                }
        );
    }
    //将店铺数据按照typeId存入Redis
    @Test
    public void loadShopListToCache(){
        //获取店铺数据
        List<Shop> shopList=shopService.list();
        //查询店铺的typeId,将店铺的TypeId和店铺的Id对应
        Map<Long,List<Shop> > shopMap=shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //分类将店铺数据写入Redis中的不同Key中
        for(Map.Entry<Long,List<Shop>> shopMapEntry:shopMap.entrySet()){
            //提取每个shop的typeId和Id
            Long typeId=shopMapEntry.getKey();
            String key=SHOP_GEO_KEY+typeId;
            List<Shop> values=shopMapEntry.getValue();
            //将Key和Value根据TypeId放入StringRedisTemplate中
                //建立一个list将当前shop的Key和Value对象批量写入
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>();
            for(Shop shop:values){
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));

            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
    //HyperLogLog测试
    @Test
    void testHyperLogLog(){
        String[] users=new String[1000];
        int index=0;

        //将数据注入到Redis中
        for(int i=0;i<1000000;i++){
            //向需要进行统计的数组中插入数据
            users[index]="user_"+i;
            index++;
            //如果i是1000的倍数，则将index清空，设立新的键来注入
            if(index==1000){
                index=0;
                stringRedisTemplate.opsForHyperLogLog().add("hill1",users);
            }
            //统计已经被假注入的键值对数量
            Long size=stringRedisTemplate.opsForHyperLogLog().size("hill1");
            System.out.println("size="+size);

        }
    }

}
