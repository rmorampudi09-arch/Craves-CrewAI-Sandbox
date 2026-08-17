package in.craves.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class CartControllerReorderContractTest {
    @Test
    void publishesCustomerReorderAsPostUnderCartBoundary() {
        Method reorder = Arrays.stream(CartController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("reorder"))
            .findFirst()
            .orElseThrow();
        PostMapping mapping = reorder.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/reorder/{orderId}");
    }
}
