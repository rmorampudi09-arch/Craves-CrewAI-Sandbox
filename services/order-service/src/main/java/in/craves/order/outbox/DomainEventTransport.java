package in.craves.order.outbox;

public interface DomainEventTransport {
    String publish(OrderDomainOutboxRecord record);
}
