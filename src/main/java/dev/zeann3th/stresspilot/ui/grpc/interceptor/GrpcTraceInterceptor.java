package dev.zeann3th.stresspilot.ui.grpc.interceptor;

import io.grpc.*;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@GlobalServerInterceptor
@RequiredArgsConstructor
public class GrpcTraceInterceptor implements ServerInterceptor {

    private final ObjectProvider<Tracer> tracerProvider;

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
                Tracer tracer = tracerProvider.getIfAvailable();
                if (tracer != null) {
                    Optional.ofNullable(tracer.currentSpan())
                            .map(span -> span.context().traceId())
                            .ifPresent(traceId -> responseHeaders.put(TRACE_ID_KEY, traceId));
                }
                super.sendHeaders(responseHeaders);
            }
        }, headers);
    }
}
