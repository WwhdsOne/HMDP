package com.hmdp.componet;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherOrderMsg;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Producer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void produce() {
        VoucherOrderMsg voucherOrderMsg = VoucherOrderMsg
                .builder()
                .voucherId(11L)
                .userId(1011L)
                .build();
        rabbitTemplate.convertAndSend("Voucher_SecKill", voucherOrderMsg);
    }
}