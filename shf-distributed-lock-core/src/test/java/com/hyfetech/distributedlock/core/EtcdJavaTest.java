package com.hyfetech.distributedlock.core;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.ibm.etcd.api.PutResponse;
import com.ibm.etcd.api.RangeResponse;
import com.ibm.etcd.client.EtcdClient;
import com.ibm.etcd.client.KvStoreClient;
import com.ibm.etcd.client.kv.KvClient;

import java.nio.charset.StandardCharsets;

/**
 * etcd-java测试
 * @author shanghaifei
 * @date 2021/9/6
 */
public class EtcdJavaTest {
    public static void main(String[] args) {
        // 创建client
        KvStoreClient client = EtcdClient.forEndpoint("127.0.0.1", 2379).withPlainText().build();
        KvClient kvClient = client.getKvClient();
        // key
        ByteString key = ByteString.copyFrom("test_key", StandardCharsets.UTF_8);
        // value
        ByteString value = ByteString.copyFrom("test_value", StandardCharsets.UTF_8);
        // 添加
        PutResponse putResult = kvClient.put(key, value).sync();
        // 获取
        ListenableFuture<RangeResponse> getResult = kvClient.get(key).timeout(5000).async();
    }
}
