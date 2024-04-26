package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherOrderMsg;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service("voucherOrderServiceWithRedisStream")
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private IVoucherOrderService proxy;

    @RabbitListener(queuesToDeclare = @Queue("Voucher_SecKill"), ackMode = "MANUAL")
    public void processVoucherOrderMsg(VoucherOrder voucherOrder, Channel channel, Message msg) {
        try {
            log.info("接收到消息:{}", voucherOrder);
            // 处理消息...
            handleVoucherOrder(voucherOrder);
            // 手动确认消息
            channel.basicAck(msg.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理消息时发生异常.", e);
            try {
                // 如果处理消息时发生异常，那么拒绝消息并将其放回队列
                channel.basicNack(msg.getMessageProperties().getDeliveryTag(), false, true);
            } catch (IOException ioException) {
                throw RabbitExceptionTranslator.convertRabbitAccessException(ioException);
            }
        }
    }


    //阻塞队列中处理优惠卷
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1. 获取锁对象
        Long userId = voucherOrder.getUserId();
        //2. 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3. 获取锁
        boolean isLock = lock.tryLock();
        //4. 判断锁是否获取成功
        if ( !isLock ) {
            //加锁失败
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result secKillVoucher(Long voucherId) {
        //1. 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .createTime(LocalDateTime.now())
                .build();
        //2. 判断结果为0
        int r = result.intValue();
        if ( r != 0 ) {
            //2.1 结果不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //3. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4. 发送消息
        rabbitTemplate.convertAndSend("Voucher_SecKill", voucherOrder);
        //5. 返回订单id
        return Result.ok(orderId);
    }


    @Transactional
    public void createVoucherOder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //5.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2 判断是否存在
        if ( count > 0 ) {
            log.error("每人限购一张");
            return;
        }
        //6. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where
                .update();
        if ( !success ) {
            log.error("库存不足");
            return;
        }
        save(voucherOrder);
    }
}
