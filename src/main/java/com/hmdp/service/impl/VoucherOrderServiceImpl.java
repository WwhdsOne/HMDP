package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result secKillVoucher(Long voucherId) {
        //1. 查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. 判断秒杀是否开始
        if ( voucher.getBeginTime().isAfter(LocalDateTime.now()) ) {
            //尚未开始秒杀活动
            return Result.fail("秒杀未开始");
        }
        //3. 判断秒杀是否结束
        if ( voucher.getEndTime().isBefore(LocalDateTime.now()) ) {
            //秒杀活动已结束
            return Result.fail("秒杀已结束");
        }
        //4. 判断库存是否充足
        if ( voucher.getStock() <= 0 ) {
            //库存不足
            return Result.fail("库存不足");
        }
        //5. 一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //5.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2 判断是否存在
        if ( count > 0 ) {
            return Result.fail("每人限购一张");
        }
        //6. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set
                .eq("voucher_id", voucherId).gt("stock", 0) // where
                .update();
        if ( !success ) {
            return Result.fail("库存不足");
        }

        //7. 生成订单
        VoucherOrder order = new VoucherOrder();
        //7.1 生成订单Id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        //7.2 用户id
        order.setUserId(UserHolder.getUser().getId());
        //7.3 优惠券id
        order.setVoucherId(voucherId);
        save(order);
        //8. 返回订单Id
        return Result.ok(orderId);
    }
}
