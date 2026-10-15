package com.amatrix.sic.material.storage;

import com.amatrix.sic.material.config.properties.MaterialProperties;

import java.io.InputStream;

import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;

/**
 * 初始化检查/创建 bucket，封装对象 put/get/remove 与存储异常。
 */
@Component
public class MinioObjectStorage{
    private final MinioClient client;private final MaterialProperties properties;
    public MinioObjectStorage(MinioClient client,MaterialProperties properties){this.client=client;this.properties=properties;}
    /** 多副本同时初始化时，只容忍 bucket 创建竞争；权限、网络等其他错误继续失败。 */
    @PostConstruct public void ensureBucket(){
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                try { client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build()); }
                catch (io.minio.errors.ErrorResponseException conflict) {
                    String code = conflict.errorResponse().code();
                    if (!("BucketAlreadyOwnedByYou".equals(code) || "BucketAlreadyExists".equals(code))
                            || !client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) throw conflict;
                }
            }
        } catch(Exception ex){throw new IllegalStateException("Unable to initialize MinIO bucket",ex);}
    }
    public void put(String key,InputStream stream,long size,String contentType){try{client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(key).stream(stream,size,-1).contentType(contentType).build());}catch(Exception ex){throw new IllegalStateException("Unable to store material",ex);}}
    public InputStream get(String key){try{return client.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(key).build());}catch(Exception ex){throw new IllegalStateException("Unable to read material",ex);}}
    public void remove(String key){try{client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());}catch(Exception ex){throw new IllegalStateException("Unable to remove material",ex);}}
}
