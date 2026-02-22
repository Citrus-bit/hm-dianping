package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDWorkder;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */


@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIDWorkder redisIdWorker;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 2. 定义lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 3. 定义阻塞队列
    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new voucherOrderHandler());
    }


    // 4. 定义线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private class voucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单
                    VoucherOrder voucherOrder = orderQueue.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }
    
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户id
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁
        boolean isLock = lock.tryLock();
        // 4.判断是否获取成功
        if (!isLock) {
            // 4.1 获取锁失败，直接返回失败或重试
            log.error("获取锁失败,用户id: {}", userId);
            return;
        }
        try {
            // 4.1 获取锁成功，处理订单
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 5.释放锁
            lock.unlock();
        }
        
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(),
            userId.toString()
        );
        

        // 1.2 判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 1.2.1 不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "您已购买过一次");
        }


        // 购买成功将id放到阻塞队列
        // 3. 获取订单id
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        // 4. 填充订单信息
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 放入阻塞队列
        orderQueue.add(voucherOrder);

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        
        // 6. 返回订单id
        return Result.ok(orderId);
    }






    /* @Override
    public Result seckillVoucher(Long voucherId) {
        // 0. 判断用户是否登录
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        
        // 1.判断秒杀券是否存在
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        // 判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 2.判断优惠券库存是否充足
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("优惠券已售罄");
        }

        // 获取用户id
        Long userId = UserHolder.getUser().getId();

        // Redisson 分布式锁
        // 获取锁对象
        RLock lockObj = redissonClient.getLock("lock:order:" + userId);
        // 4.尝试获取锁
        boolean isLock = lockObj.tryLock();
        // 5.判断是否获取锁成功
        if (!isLock) {
            return Result.fail("一人只能购买一次");
        }

        // 6. 成功，创建订单
        try {
            return createVoucherOrder(voucherId);
        } finally {
            // 7. 释放锁
            lockObj.unlock();
        } */


        /* // 3.创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        // 4.尝试获取锁
        boolean isLock = lock.tryLock(1);
        // 5.判断是否获取锁成功
        if (!isLock) {
            return Result.fail("一人只能购买一次");
        }

        // 6. 成功，创建订单
        try {
            return createVoucherOrder(voucherId);
        } finally {
            // 7. 释放锁
            lock.unlock();
        } */
        


        /* synchronized (userId.toString().intern()) {
            // 获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } 
    } */    

        
        




    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }
        save(voucherOrder);
        
        return Result.ok();
    }

}
