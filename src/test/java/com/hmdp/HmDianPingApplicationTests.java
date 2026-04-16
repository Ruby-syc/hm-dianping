package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    //写个线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () ->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            //一个线程执行完毕，就执行一下这个
            latch.countDown();
        };
        //线程池是异步的，这个计时没有意义
        long begin = System.currentTimeMillis();
        //300个线程，一个线程创建100个id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //等到所以CountDown结束为止，再记录结束时间
        latch.await();
        long end = System.currentTimeMillis();
        //再打印一下
        System.out.println("time:" + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        //shopService.saveShop2Redis(1L,10L);
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


}
