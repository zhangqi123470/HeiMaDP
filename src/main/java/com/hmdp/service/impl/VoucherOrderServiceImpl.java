package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;//定义静态变量,并为这个静态变量赋予初值
    static{
            SECKILL_SCRIPT=new DefaultRedisScript<>();
            SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
            SECKILL_SCRIPT.setResultType(Long.class);


    }
    //创建线程实现订单的存取
    //建立一个阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    @PostConstruct
    //制定类开始时执行的操作
    private void init(){
        //监测队列中是否有待执行的任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());//提交
    }
    //
    private class VoucherOrderHandler implements Runnable{
        public void run(){
            //检测线程池中是否有需要完成的任务
            try{
                //获取订单中的订单信息
                VoucherOrder voucherOrder=orderTasks.take();
                //处理订单

            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    //线程任务:从阻塞队列中添加订单
    private IVoucherOrderService proxy;
    private void handlerVoucher(VoucherOrder voucherOrder){
        //获取锁,然后将订单添加进数据库
        Long userId=voucherOrder.getId();
        RLock lock=redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY+userId);
        boolean isLock=lock.tryLock();
        //如果没有获取锁,则返回报错信息
        if(!isLock){
            log.info("用户只能下一单!");
        }
        //创建订单
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally{
            lock.unlock();
        }
    }
    private IVoucherService proxy;
    @Override
//    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 通过lua脚本查询优惠券
        //stringRedisTemplate调用lua脚本查询
        Long userId=UserHolder.getUser().getId();
        Long result=stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                //传递的参数
        Collections.emptyList(),
                //传递的args
                voucherId.toString(),userId.toString()

        );
        //如果result不为0
        if(result!=0){
            //条件表达式
            return Result.fail(result==1?"库存不足":"用户不能重复下单");
        }
        RedisIdWorker redisIdworker=new RedisIdWorker();
        //生成订单号id
        long orderId=redisIdworker.nextId("order");
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //将voucherOrder放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象，将voucher添加进数据库中


        RLock Lock=redissonClient.getLock("lock:order:"+userId);
        //如果获取锁失败，则返回报错信息
        boolean isLock=Lock.tryLock();
        if(!isLock){
            return Result.fail("不允许重复下单！");
        }

        try{
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherOrder);
        }finally{
            Lock.unlock();
        }

    }
    //将创建订单的功能分离开
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        log.info("创建订单");
        Long userId=voucherOrder.getId();
        Long voucherId=voucherOrder.getVoucherId();
        //判断当前用户是否是第一单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getUserId, userId));
        //如果是第一单的话，为该用户创建订单
        if (count >= 1) {
            return Result.fail("用户已经购买");
        }
        //库存订单减少1
        boolean flag = this.save(voucherOrder);
        if (!flag) {
            throw new RuntimeException("秒杀券扣减失败");
        }
        //在voucer_order表中添加一个订单
        return Result.ok();
    }

}
