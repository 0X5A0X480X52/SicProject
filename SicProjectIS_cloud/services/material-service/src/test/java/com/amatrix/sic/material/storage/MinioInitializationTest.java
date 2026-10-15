package com.amatrix.sic.material.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.Test;
import com.amatrix.sic.material.config.properties.MaterialProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

class MinioInitializationTest {
    @Test void verifiesTheBucketAfterAnotherReplicaCreatesIt() throws Exception {
        var client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false, true);
        doThrow(error("BucketAlreadyOwnedByYou")).when(client).makeBucket(any(MakeBucketArgs.class));
        storage(client).ensureBucket();
        verify(client, times(2)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test void doesNotHideAccessErrorsOrAnAbsentBucket() throws Exception {
        for (String code : new String[]{"AccessDenied", "BucketAlreadyExists"}) {
            var client = mock(MinioClient.class);
            when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
            var cause = error(code);
            doThrow(cause).when(client).makeBucket(any(MakeBucketArgs.class));
            assertThatThrownBy(() -> storage(client).ensureBucket()).hasCause(cause);
        }
    }

    private static MinioObjectStorage storage(MinioClient client) {
        return new MinioObjectStorage(client, new MaterialProperties(null, null, null, "sic-materials"));
    }

    private static ErrorResponseException error(String code) {
        var error = mock(ErrorResponseException.class);
        var response = mock(ErrorResponse.class);
        when(error.errorResponse()).thenReturn(response);
        when(response.code()).thenReturn(code);
        return error;
    }
}
