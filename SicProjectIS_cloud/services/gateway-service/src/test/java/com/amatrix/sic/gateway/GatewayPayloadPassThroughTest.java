package com.amatrix.sic.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class GatewayPayloadPassThroughTest {
    private final TraceIdWebFilter traceFilter = new TraceIdWebFilter();

    @Test
    void preservesMultipartContentTypeAndBodyWithoutBufferingOrRewritingIt() {
        String boundary = "sic-boundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"materialTypeId\"\r\n\r\n12\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"evidence.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\nevidence-bytes\r\n"
                + "--" + boundary + "--\r\n";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/projects/7/materials")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(body));
        AtomicReference<String> forwardedBody = new AtomicReference<>();
        AtomicReference<String> forwardedContentType = new AtomicReference<>();
        WebFilterChain downstream = forwarded -> {
            forwardedContentType.set(forwarded.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            return DataBufferUtils.join(forwarded.getRequest().getBody()).doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                forwardedBody.set(new String(bytes, StandardCharsets.UTF_8));
            }).then();
        };

        traceFilter.filter(exchange, downstream).block();

        assertThat(forwardedContentType.get()).isEqualTo("multipart/form-data;boundary=" + boundary);
        assertThat(forwardedBody.get()).isEqualTo(body);
    }

    @Test
    void preservesStreamingDownloadChunksAndContentDisposition() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/material-versions/99/download")
                .header(TraceIdWebFilter.TRACE_HEADER, "trace-download-123"));
        WebFilterChain downstream = forwarded -> {
            forwarded.getResponse().getHeaders().add(TraceIdWebFilter.TRACE_HEADER, "downstream-copy");
            forwarded.getResponse().getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
            forwarded.getResponse().getHeaders().setContentDisposition(
                    ContentDisposition.attachment().filename("验收材料.pdf", StandardCharsets.UTF_8).build());
            var buffers = forwarded.getResponse().bufferFactory();
            return forwarded.getResponse().writeWith(Flux.just(
                    buffers.wrap("first-".getBytes(StandardCharsets.UTF_8)),
                    buffers.wrap("second".getBytes(StandardCharsets.UTF_8))));
        };

        traceFilter.filter(exchange, downstream).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("first-second");
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(exchange.getResponse().getHeaders().getContentDisposition().getFilename())
                .isEqualTo("验收材料.pdf");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdWebFilter.TRACE_HEADER))
                .isEqualTo("trace-download-123");
        assertThat(exchange.getResponse().getHeaders().getValuesAsList(TraceIdWebFilter.TRACE_HEADER))
                .containsExactly("trace-download-123");
    }
}
