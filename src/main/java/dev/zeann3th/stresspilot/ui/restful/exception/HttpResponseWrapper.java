package dev.zeann3th.stresspilot.ui.restful.exception;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

@ControllerAdvice
@RequiredArgsConstructor
public class HttpResponseWrapper implements ResponseBodyAdvice<Object> {

    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getContainingClass().isAnnotationPresent(ResponseWrapper.class)
                || returnType.hasMethodAnnotation(ResponseWrapper.class);
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            @NonNull MethodParameter returnType,
            @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }

        var apiResponse = ApiResponse.success(body);

        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return jsonMapper.writeValueAsString(apiResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize string response", e);
            }
        }

        return apiResponse;
    }
}
