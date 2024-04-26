package com.hmdp.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/25 下午10:06
 * @description 代金券订单消息
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherOrderMsg implements Serializable {
    private Long voucherId;
    private Long userId;
    private Long orderId;
}
