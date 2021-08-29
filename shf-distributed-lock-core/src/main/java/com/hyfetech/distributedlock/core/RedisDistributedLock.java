package com.hyfetech.distributedlock.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁
 * @author shanghaifei
 * @date 2021/8/26
 */
public class RedisDistributedLock implements DistributedLock {
    //region 变量

    /**
     * JedisPool
     */
    private JedisPool jedisPool;

    /**
     * Redis主机
     */
    private String redisHost;

    /**
     * Redis端口
     */
    private int redisPort;

    /**
     * Redis密码
     */
    private String redisPassword;

    /**
     * 定时线程池
     */
    private ScheduledExecutorService watchDogScheduledExecutorService;

    /**
     * 锁的失效毫秒数（设置失效时间，防止因为连接断开而导致死锁）
     */
    private int lockTtlMills = 100;

    //endregion

    /**
     * 无参构造函数（默认使用localhost:6379）
     */
    public RedisDistributedLock() {
        // 初始化JedisPool，通过JedisPool获取Jedis连接，因为Jedis在多线程下有问题
        jedisPool = new JedisPool();
    }

    /**
     * 构造函数
     * @param host redis主机
     * @param port redis端口
     * @param password redis密码
     */
    public RedisDistributedLock(String host,int port,String password) {
        this.redisHost = host;
        this.redisPort = port;
        this.redisPassword = password;
        // 初始化JedisPool，通过JedisPool获取Jedis连接，因为Jedis在多线程下有问题
        jedisPool = new JedisPool(this.redisHost,this.redisPort);
        // 定时线程池
        watchDogScheduledExecutorService = Executors.newScheduledThreadPool(10);
    }

    /**
     * 获取锁（阻塞）
     * @param lockName 锁名称
     * @return 是否获取成功
     */
    @Override
    public void lock(String lockName) throws Exception {
        Jedis jedisClient = openClient();
        try {
            // 当前线程ID
            long currentThreadId = Thread.currentThread().getId();
            String currentThreadIdStr = String.valueOf(currentThreadId);
            while (true) {
                // setnx + pexpire命令结合的lua脚本
                String setNxAndExpireScript =
                        "local result = redis.call('setnx',KEYS[1],ARGV[1])\n" +
                        "if(result == 1) then\n" +
                        "    local result = redis.call('pexpire',KEYS[1],ARGV[2])\n" +
                        "    return result\n" +
                        "else\n" +
                        "    return result\n" +
                        "end";
                List<String> keysList = new ArrayList<>();
                // key
                keysList.add(lockName);
                List<String> argsList = new ArrayList<>();
                // value
                argsList.add(String.valueOf(currentThreadId));
                // 过期时间
                argsList.add(String.valueOf(lockTtlMills));
                Long setNxAndExpireResult = Long.parseLong(
                        jedisClient.eval(setNxAndExpireScript, keysList, argsList).toString());
                if (setNxAndExpireResult == 1) {
                    // 开启一个定时线程，如果锁未释放，定时进行续约
                    startRenewTtlThread(jedisClient,lockName,currentThreadIdStr);
                    break;
                }
                // 获取不到锁，则停顿个10毫秒继续尝试获取
                Thread.sleep(50);
            }
        }
        finally {
            jedisClient.close();
        }
    }

    /**
     * 获取锁（阻塞），没有使用lua脚本，无法保证setnx和expire的原子性
     * @param lockName 锁名称
     * @return 是否获取成功
     */
    public void lockNonLuaScript(String lockName) throws Exception {
        Jedis jedisClient = openClient();
        try {
            // 当前线程ID
            long currentThreadId = Thread.currentThread().getId();
            String currentThreadIdStr = String.valueOf(currentThreadId);
            while (true) {
                Long setNxResult = jedisClient.setnx(lockName, currentThreadIdStr);
                if (setNxResult == 1) {
                    // 设置锁过期（防止死锁）
                    jedisClient.pexpire(lockName, lockTtlMills);
                    // 开启一个定时线程，如果锁未释放，定时进行续约
                    startRenewTtlThread(jedisClient,lockName,currentThreadIdStr);
                    break;
                }
                // 获取不到锁，则停顿个10毫秒继续尝试获取
                Thread.sleep(50);
            }
        }
        finally {
            jedisClient.close();
        }
    }

    /**
     * 尝试获取锁（非阻塞）
     * @param lockName 锁名称
     * @param waitTime 等待时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    @Override
    public boolean tryLock(String lockName,Long waitTime, TimeUnit timeUnit) {
        return false;
    }

    /**
     * 释放锁
     * @param lockName 锁名称
     * @return 是否释放成功
     */
    @Override
    public boolean release(String lockName) throws Exception {
        Jedis jedisClient = openClient();
        try {
            // 当前线程ID
            long currentThreadId = Thread.currentThread().getId();
            // 锁值（当前拿到锁的线程ID）
            String lockValue = jedisClient.get(lockName);
            if (lockValue.equals(Long.toString(currentThreadId))) {
                Long delResult = jedisClient.del(lockName);
                return delResult == 1;
            }
            return false;
        }
        finally {
            jedisClient.close();
        }
    }

    /**
     * 开启续约过期时间的线程
     * @param jedisClient Jedis客户端
     * @param lockName 锁名称
     * @param renewThreadId 续约的线程ID
     */
    public void startRenewTtlThread(Jedis jedisClient,String lockName,String renewThreadId) {
        watchDogScheduledExecutorService.scheduleAtFixedRate(() -> {
            String lockValue = jedisClient.get(lockName);
            if(lockValue != null && lockValue.equals(renewThreadId)) {
                jedisClient.pexpire(lockName,lockTtlMills);
            }
            else {
                // 打断线程，退出
                Thread.currentThread().interrupt();
            }
        },0,50,TimeUnit.MILLISECONDS);
    }

    //region Jedis相关

    /**
     * 打开连接
     * @return
     */
    public Jedis openClient() {
        //Jedis jedis = jedisPool.getResource()
        Jedis jedis = new Jedis(redisHost,redisPort);
        return jedis;
    }

    //endregion
}
