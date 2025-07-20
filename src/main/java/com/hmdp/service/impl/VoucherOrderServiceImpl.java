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
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 直接在代码中定义 Lua 脚本，避免文件读取问题
        String script = "local voucherId = ARGV[1] " +
                "local userId = ARGV[2] " +
                "local stockKey = \"seckill:stock:\" .. voucherId " +
                "local orderKey = \"seckill:order:\" .. voucherId " +
                "local stock = redis.call('get', stockKey) " +
                "if stock == nil then return 1 end " +
                "local stockNum = tonumber(stock) " +
                "if stockNum == nil or stockNum <= 0 then return 1 end " +
                "local isMember = redis.call('sismember', orderKey, userId) " +
                "if isMember == 1 then return 2 end " +
                "redis.call('incrby', stockKey, -1) " +
                "redis.call('sadd', orderKey, userId) " +
                "return 0";
        SECKILL_SCRIPT.setScriptText(script);
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 创建线程实现订单的存取
    // 建立一个阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    // 制定类开始时执行的操作
    private void init() {
        // 监测队列中是否有待执行的任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());// 提交
    }

    //
    private class VoucherOrderHandler implements Runnable {
        public void run() {
            // 检测线程池中是否有需要完成的任务
            try {
                // 获取订单中的订单信息
                VoucherOrder voucherOrder = orderTasks.take();
                // 处理订单

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 线程任务:从阻塞队列中添加订单
    private IVoucherOrderService proxy;

    private void handlerVoucher(VoucherOrder voucherOrder) {
        // 获取锁,然后将订单添加进数据库
        Long userId = voucherOrder.getId();
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        boolean isLock = lock.tryLock();
        // 如果没有获取锁,则返回报错信息
        if (!isLock) {
            log.info("用户只能下一单!");
        }
        // 创建订单
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();

        }
    }

    @Override
    // @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 通过lua脚本查询优惠券
        // stringRedisTemplate调用lua脚本查询
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                // 传递的参数
                Collections.emptyList(),
                // 传递的args
                voucherId.toString(), userId.toString()

        );

        log.info("Lua脚本执行结果: voucherId={}, userId={}, result={}", voucherId, userId, result);

        // 如果result不为0
        if (result != null && result != 0) {
            // 条件表达式
            log.info("Lua脚本返回失败: result={}", result);
            return Result.fail(result == 1 ? "库存不足" : "用户不能重复下单");
        }

        log.info("Lua脚本执行成功，开始创建订单");
        // 生成订单号id
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 将voucherOrder放入阻塞队列
        orderTasks.add(voucherOrder);
        log.info("订单已加入队列: orderId={}", orderId);

        // 获取代理对象，将voucher添加进数据库中
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 如果获取锁失败，则返回报错信息
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.info("获取锁失败: userId={}", userId);
            return Result.fail("不允许重复下单！");
        }

        try {
            log.info("获取锁成功，开始创建订单: userId={}", userId);
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            Result orderResult = proxy.createVoucherOrder(voucherOrder);
            log.info("订单创建结果: {}", orderResult);
            return orderResult;
        } finally {
            lock.unlock();
            log.info("释放锁: userId={}", userId);
        }

    }

    // 将创建订单的功能分离开
   @Autowired
   private SeckillVoucherServiceImpl setkillVoucherServiceImpl;
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        log.info("创建订单");
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 判断当前用户是否是第一单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        // 如果是第一单的话，为该用户创建订单
        if (count >= 1) {
            return Result.fail("用户已经购买");
        }
        // 库存订单减少1
        boolean update=setkillVoucherServiceImpl.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId,voucherId)
                        .gt(SeckillVoucher::getStock,0)
                        .setSql("stock=stock-1")
        );
        if(!update){
            return Result.fail("库存不足！");
        }
        boolean flag = this.save(voucherOrder);
        if (!flag) {
            throw new RuntimeException("秒杀券扣减失败");
        }
        // 在voucer_order表中添加一个订单
        return Result.ok();
    }

}
