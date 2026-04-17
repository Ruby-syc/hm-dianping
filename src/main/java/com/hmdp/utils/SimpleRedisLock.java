package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate ) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";//true 把uuid里面的横线去除掉
    //提前初始化
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //调用脚本，设置脚本位置，在classpath下找
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //配一下返回值
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        //long threadId = Thread.currentThread().getId();
        String threadId =ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        /*Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);*/
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //return success;自动拆箱可能空指针异常
        return Boolean.TRUE.equals(success);
    }


    @Override
    public void unlock() {
       //调用LUA脚本，把key变成单元素集合
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

   /* @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断是否一致
        if (threadId.equals(id)) {
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
