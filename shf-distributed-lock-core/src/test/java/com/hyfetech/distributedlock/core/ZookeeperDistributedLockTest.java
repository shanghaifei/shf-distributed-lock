package com.hyfetech.distributedlock.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Zookeeper分布式锁测试
 * @author shanghaifei
 * @date 2021/8/26
 */
public class ZookeeperDistributedLockTest {
    private static int sum = 0;

    public static void main(String[] args) throws Exception {
        // 执行次数
        int executeTime = 5000;
        // 线程池
        ExecutorService executorService = Executors.newFixedThreadPool(20);

        // 普通累加
        CountDownLatch countDownLatch1 = new CountDownLatch(executeTime);
        for(int i = 0;i < executeTime;i++) {
            executorService.submit(() -> {
                try {
                    sum = sum + 1;
                }
                catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                finally {
                    countDownLatch1.countDown();
                }
            });
        }
        countDownLatch1.await();
        System.out.println("未使用分布式锁：" + sum);

        // 使用分布式锁累加
        sum = 0;
        String lockName = "/sum-lock";
        DistributedLock distributedLock = new ZookeeperDistributedLock();
        CountDownLatch countDownLatch2 = new CountDownLatch(executeTime);
        for(int i = 0;i < executeTime;i++) {
            executorService.submit(() -> {
                try {
                    distributedLock.lock(lockName);
                    sum = sum + 1;
                }
                catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
                finally {
                    countDownLatch2.countDown();
                    try {
                        distributedLock.release(lockName);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        countDownLatch2.await();
        System.out.println("使用分布式锁：" + sum);
    }
}
