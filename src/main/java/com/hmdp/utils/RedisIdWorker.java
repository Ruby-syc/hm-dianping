package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //1.生成当前时间戳-31bit，最前面一位为符号位
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);//当前秒数
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号，redis自增长，32位
        //2.1.获取当前日期，精确到天，格式化，自定义，方便统计
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长，用基本类型long，不要用包装类，因为下面要做计算，不会空指针，key不存在的话，会自动创建一个key
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3。拼接并返回，直接拼接是字符串了。位运算解决，时间戳向左移动32位

        return timestamp << COUNT_BITS | count;//或运算
    }

    //psvm
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.now().of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);//写时区,得到具体秒数
        System.out.println("second:" + second);
    }
}
