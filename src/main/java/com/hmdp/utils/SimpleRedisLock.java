package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author Wwh
 * @ProjectName hm-dianping
 * @dateTime 2024/4/11 下午4:37
 * @description redis互斥锁
 **/
public class SimpleRedisLock implements ILock{

    private String name;
    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    @Override
    public boolean lock(long timeoutSec) {
        //获取线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                Collections.singletonList(ID_PREFIX + Thread.currentThread().getId()));
    }

//    @Override
//    public void unlock() {
//        //获取线程id
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取redis锁id
//        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(threadId.equals(lockId)){
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
