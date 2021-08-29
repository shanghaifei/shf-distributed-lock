package com.hyfetech.distributedlock.core;

import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * zookeeper分布式锁
 * @author shanghaifei
 * @date 2021/8/26
 */
public class ZookeeperDistributedLock implements DistributedLock, Watcher {
    //region 变量

    /**
     * zookeeper地址
     */
    private String zkAddress;

    /**
     * 端口
     */
    private int port;

    /**
     * Zookeeper客户端
     */
    private ZooKeeper zkClient;

    /**
     * 当前使用park的线程集合
     */
    private Map<String,Thread> parkThreadsMap = new ConcurrentHashMap<>();

    /**
     * 需要unPark的NodePath
     */
    private String unParkNodePath;

    //endregion

    /**
     * 默认连接localhost:2181的zookeeper
     */
    public ZookeeperDistributedLock() {
        this.zkAddress = "localhost";
        this.port = 2181;
        String address = zkAddress + ":" + port;
        try {
            zkClient = new ZooKeeper(address,2000,this);
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    /**
     * 构造函数
     * @param zkAddress zookeeper地址
     * @param port zookeeper端口
     */
    public ZookeeperDistributedLock(String zkAddress,int port) {
        this.zkAddress = zkAddress;
        this.port = port;
    }

    /**
     * 获取锁（阻塞）
     *
     * @param lockName
     */
    @Override
    public void lock(String lockName) throws Exception {
        // 线程ID
        long threadId = Thread.currentThread().getId();
        String threadIdStr = String.valueOf(threadId);
        // 创建zookeeper的临时顺序节点
        String[] paths = lockName.split("/");
        String persistentNodePath = "";
        if(paths != null && paths.length > 0) {
            for(String path : paths) {
                if(StringUtils.isNotEmpty(path)) {
                    persistentNodePath += ("/" + path);
                    // 创建持久节点（临时节点必须存放在持久节点下面）
                    createNode(persistentNodePath, null,CreateMode.PERSISTENT,true);
                }
            }
        }
        String ephemeralNodeParentPath = persistentNodePath + "/lock";
        // 临时节点最终path
        String ephemeralNodePath = createNode(ephemeralNodeParentPath,threadIdStr,CreateMode.EPHEMERAL_SEQUENTIAL,
                true);
        // 获取当前锁下面的等待列表
        List<String> childrenNodePaths = zkClient.getChildren(persistentNodePath, false);
        childrenNodePaths.sort(null);
        if(!childrenNodePaths.isEmpty()) {
            // 获取当前节点名称
            int index = ephemeralNodePath.lastIndexOf("/");
            String ephemeralNodeName = ephemeralNodePath.substring(index + 1);
            // 判断当前节点是否在等待列表的首位，如果不是，则线程进行等待
            if(childrenNodePaths.indexOf(ephemeralNodeName) > 0) {
                parkThreadsMap.put(ephemeralNodePath,Thread.currentThread());
                // 当前线程park
                LockSupport.park();
            }
        }
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
        // 线程ID
        long threadId = Thread.currentThread().getId();
        // 获取当前锁下面的所有节点
        List<String> childrenNodes = zkClient.getChildren(lockName, false);
        if(!childrenNodes.isEmpty()) {
            childrenNodes.sort(null);
            // 第一个节点的path
            String firstNodePath = lockName + "/" + childrenNodes.get(0);
            // 获取第一个节点的状态Stat对象
            Stat firstNodeStatus = zkClient.exists(firstNodePath, false);
            // 获取第一个节点的数据
            String firstNodeData = new String(zkClient.getData(firstNodePath, false, firstNodeStatus));
            if(firstNodeStatus != null && StringUtils.isNotEmpty(firstNodeData) && firstNodeData.equals(String.valueOf(threadId))) {
                // 设置下一个需要唤醒的节点（当前删除节点的下一个节点）
                if(childrenNodes.size() >= 2) {
                    unParkNodePath = lockName + "/" + childrenNodes.get(1);
                }
                // 删除节点
                zkClient.delete(firstNodePath, firstNodeStatus.getVersion());
                return true;
            }
        }
        return false;
    }

    /**
     * 监听事件处理
     * @param watchedEvent
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        // 节点的路径
        String nodePath = watchedEvent.getPath();
        // 事件类型
        Event.EventType type = watchedEvent.getType();
        // 事件状态
        Event.KeeperState state = watchedEvent.getState();
        if(state == Event.KeeperState.SyncConnected) {
            // 节点删除事件
            if (type == Event.EventType.NodeDeleted) {
                try {
                    if(StringUtils.isNotEmpty(unParkNodePath)) {
                        Thread unParkNodeThread = parkThreadsMap.get(unParkNodePath);
                        LockSupport.unpark(unParkNodeThread);
                    }
                }
                catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }
    }

    //region 公共方法

    /**
     * 创建节点
     * @param path 路径
     * @param data 数据
     * @param createMode 创建模式
     * @param isWatch 是否监控
     * @return 节点路径
     * @throws Exception
     */
    public String createNode(String path,String data,CreateMode createMode,
                             boolean isWatch) throws Exception {
        if(createMode == CreateMode.PERSISTENT && zkClient.exists(path, isWatch) != null) {
            return null;
        }
        String resultPath = zkClient.create(path, StringUtils.isNotEmpty(data) ? data.getBytes() : null,
                ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        if(createMode == CreateMode.EPHEMERAL_SEQUENTIAL) {
            zkClient.exists(resultPath,isWatch);
        }
        return resultPath;
    }

    //endregion
}
