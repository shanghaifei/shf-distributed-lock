package com.hyfetech.distributedlock.core;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口
 * @author shanghaifei
 * @date 2021/8/26
 */
public interface DistributedLock {
    /**
     * 获取锁（阻塞）
     */
    void lock(String lockName) throws Exception;

    /**
     * 尝试获取锁（非阻塞）
     * @param waitTime 等待时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String lockName,Long waitTime, TimeUnit timeUnit);

    /**
     * 释放锁
     * @return 是否释放成功
     */
    boolean release(String lockName) throws Exception;
}
