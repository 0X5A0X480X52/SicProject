package com.amatrix.sic.material;

import java.io.InputStream;

import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;

@Component
public class MinioObjectStorage{
    private final MinioClient client;private final MaterialProperties properties;
    public MinioObjectStorage(MinioClient client,MaterialProperties properties){this.client=client;this.properties=properties;}
    @PostConstruct public void ensureBucket(){try{if(!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build()))client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());}catch(Exception ex){throw new IllegalStateException("Unable to initialize MinIO bucket",ex);}}
    public void put(String key,InputStream stream,long size,String contentType){try{client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(key).stream(stream,size,-1).contentType(contentType).build());}catch(Exception ex){throw new IllegalStateException("Unable to store material",ex);}}
    public InputStream get(String key){try{return client.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(key).build());}catch(Exception ex){throw new IllegalStateException("Unable to read material",ex);}}
    public void remove(String key){try{client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());}catch(Exception ex){throw new IllegalStateException("Unable to remove material",ex);}}
}
