package com.hyfetech.distributedlock.core;

import java.util.concurrent.TimeUnit;

/**
 * Etcd分布式锁
 * @author shanghaifei
 * @date 2021/9/5
 */
public class EtcdDistributedLock implements DistributedLock {
    /**
     * 获取锁（阻塞）
     *
     * @param lockName
     */
    @Override
    public void lock(String lockName) throws Exception {

    }

    /**
     * 尝试获取锁（非阻塞）
     *
     * @param lockName
     * @param waitTime 等待时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    @Override
    public boolean tryLock(String lockName, Long waitTime, TimeUnit timeUnit) {
        return false;
    }

    /**
     * 释放锁
     *
     * @param lockName
     * @return 是否释放成功
     */
    @Override
    public boolean release(String lockName) throws Exception {
        return false;
    }
}
