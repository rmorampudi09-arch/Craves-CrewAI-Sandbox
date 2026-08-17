package in.craves.integration.delivery;

import in.craves.integration.delivery.command.DeliveryCommandProperties;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration("deliveryBaseSchedulingConfiguration")
@EnableScheduling
public class DeliverySchedulingConfiguration {

    @Bean(name = "deliveryQuoteExecutor", destroyMethod = "shutdown")
    ExecutorService deliveryQuoteExecutor(DeliveryCommandProperties properties) {
        int threadCount = Math.min(64, Math.max(4, properties.getMaxConcurrentMessages() * 4));
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(threadCount, task -> {
            Thread thread = new Thread(task);
            thread.setName("delivery-quote-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        });
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
