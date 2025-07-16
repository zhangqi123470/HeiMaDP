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
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
//    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        log.info("查询优惠券:{}",voucher);
        if(voucher==null){
            log.info("秒杀券不存在");
            return Result.fail("该秒杀券不存在!");
        }
        // 判断秒杀活动是否开始
        // 如果优惠券活动还没有开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            log.info("秒杀活动还没开始");
            return Result.fail("秒杀活动还没有开始!");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            log.info("秒杀活动那个已经结束");
            return Result.fail("秒杀活动已经结束!");

        }
        // 查询优惠券是否还有库存
        if (voucher.getStock() < 1) {
            log.info("库存不足");
            return Result.fail("库存不足!");
        }

        //实现一人一单功能’
        //如果当前订单的userId是重复的，则报错
        Long userId=UserHolder.getUser().getId();
//        int count=query().eq("user_id",userId).eq("voucher_Id",voucherId).count();
//        if(count>0){
//            return Result.fail("用户已经购买过一次!");
//        }
//        // 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId)
//                .update();
//        if (!success) {
//            log.info("库存不足");
//            return Result.fail("库存不足!");
//        }
        // 如果抢购成功，创建一个订单
        // 设置订单id和用户id
//        Long orderId = redisIdWorker.nextId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        UserDTO user = UserHolder.getUser();
//        if (user == null) {
//            return Result.fail("用户未登录");
//        }
////        Long userId = user.getId();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(user.getId());
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setCreateTime(LocalDateTime.now());
//        voucherOrder.setPayType(1); // 1:余额支付
//        voucherOrder.setStatus(1);  // 1:未支付
//        log.info("存储voucherOrder:{}",voucherOrder);
//        save(voucherOrder);
//
//        // 设置创建时间
//
//        return Result.ok(orderId);
//        synchronized(userId.toString().intern()){
//            //获取跟事务有关的代理对象
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(userId,voucherId);
//        }
        //用分布式锁为创建订单的操作上锁
        SimpleRedisLock Lock=new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        //如果获取锁失败，则返回报错信息
        boolean isLock=Lock.tryLock(1200000);
        if(!isLock){
            return Result.fail("不允许重复下单！");
        }
        //创建订单
        try{
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId,voucherId);
        }finally{
            Lock.unlock();
        }

    }
    //将创建订单的功能分离开
    @Transactional
    public Result createVoucherOrder(Long userId,Long voucherId) {
        log.info("创建订单");
        //判断当前用户是否是第一单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getUserId, userId));
        //如果是第一单的话，为该用户创建订单
        if (count >= 1) {
            return Result.fail("用户已经购买");
        }
        //库存订单减少1
        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .setSql("stock=stock-1"));
        if (!flag) {
            throw new RuntimeException("秒杀券扣减失败");
        }
        //在voucer_order表中添加一个订单
        log.info("添加秒杀券订单");
        VoucherOrder voucherOrder =new VoucherOrder();
        Long orderId=redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        return Result.ok(orderId);
    }
}
