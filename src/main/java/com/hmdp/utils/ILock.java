package com.hmdp.utils;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/11 下午4:35
 * @description redis互斥锁
 **/
public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec
     * @return 是否获取成功
     */
    boolean lock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
