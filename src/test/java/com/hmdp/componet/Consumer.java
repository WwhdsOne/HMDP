package com.hmdp.componet;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherOrderMsg;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//@Component
@Slf4j
public class Consumer {


    //@RabbitHandler
    //@RabbitListener(queuesToDeclare = @Queue("Voucher_SecKill"),ackMode = "AUTO")
    public void process(VoucherOrderMsg message) {
        log.info("接收到消息:{}",message);
    }
}
