package dev.zeann3th.stresspilot.ui.grpc.interceptor;

import io.grpc.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class GrpcTraceInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> TRACE_ID_KEY = Metadata.Key.of("x-trace-id",
            Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, A> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, A> call,
            Metadata headers,
            ServerCallHandler<Q, A> next) {

        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                SpanContext spanContext = Span.current().getSpanContext();

                if (spanContext.isValid()) {
                    responseHeaders.put(TRACE_ID_KEY, spanContext.getTraceId());
                }

                super.sendHeaders(responseHeaders);
            }
        }, headers);
    }
}
