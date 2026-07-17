package com.example.command.service;

import com.example.command.dto.CreateOrderRequest;
import com.example.command.dto.UpdateOrderStatusRequest;
import com.example.command.entity.Order;
import com.example.command.event.EventEnvelope;
import com.example.command.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String orderTopic;

    public OrderService(OrderRepository orderRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper,
                        @Value("${app.topics.order-events}") String orderTopic) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderTopic = orderTopic;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus("CREATED");
        order.setItems(toJson(request.items()));

        Order saved = orderRepository.save(order);
        kafkaTemplate.send(orderTopic, saved.getId().toString(), new EventEnvelope<>("OrderCreated", saved));
        return saved;
    }

    @Transactional
    public Order updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        order.setStatus(request.status());
        Order saved = orderRepository.save(order);

        kafkaTemplate.send(orderTopic, saved.getId().toString(),
                new EventEnvelope<>("OrderUpdated", new OrderStatusPayload(saved.getId(), saved.getStatus())));
        return saved;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order items", e);
        }
    }

    public record OrderStatusPayload(Long orderId, String newStatus) {}
}
