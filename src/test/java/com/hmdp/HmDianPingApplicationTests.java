package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Test
    void loadShopData(){
        //1.查询店铺信息
        List<Shop> list =shopService.list();
        //2.把店铺分组，按照typeId分组，typeId一致的放在一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2.获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations =new ArrayList<>(value.size());
            //3.3.写入redis GEOADD key 经度 维度 member
            for (Shop shop : value) {
                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().geoAdd(key, locations);
        }
    }

}
