package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //3.判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已经结束
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //方法二：用redis实现分布式锁
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(500);
        //判断是否获取锁成功
        if(!isLock){
            //获取锁失败，返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }

        //方法一：放在方法里，因为被事务管理有可能释放锁之后，可能发生线程安全问题，放在这里，确保事务已经提交数据库了，再释放锁
        //获取锁之后创建的事务，事务提交之后，再释放锁，避免安全问题
        /*synchronized(userId.toString().intern()) {
            //拿到事务代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/
    }
    //同步锁 放在方法上就是所有人公用一把锁，性能不高，把锁放在id上，性能会比较好，事务生效，是对这个类做了动态代理，完成事务处理，上面的调用的是this.方法，没有事务功能，spring事务会失效
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        //tostring，值一样，但是底层都是新对象，intern 返回字符串的规范表示
        //synchronized(userId.toString().intern()) {}//包到最后，这个可能线程不安全，提到外面去了
        //5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2.判断是否存在
        if (count > 0) {
            //该用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0)//乐观锁 where id = ? and stock > 0 ?
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2.用户id
        voucherOrder.setUserId(userId);
        //7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //7.返回订单id
        return Result.ok(orderId);
    }

}
