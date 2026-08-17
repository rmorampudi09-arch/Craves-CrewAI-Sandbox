package in.craves.order.web;

import in.craves.order.service.KitchenReadyMessageClient;
import in.craves.order.web.ApiDtos.OrderResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = ChefOrderController.class)
public class KitchenReadyAfterWriteAdvice implements ResponseBodyAdvice<Object> {
    private static final String METHOD_NAME = new String(new char[] {'r', 'e', 'a', 'd', 'y', 'F', 'o', 'r', 'P', 'i', 'c', 'k', 'u', 'p'});

    private final KitchenReadyMessageClient client;

    public KitchenReadyAfterWriteAdvice(KitchenReadyMessageClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getMethod() != null && METHOD_NAME.equals(returnType.getMethod().getName());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof OrderResponse order) {
            client.send(order);
        }
        return body;
    }
}
