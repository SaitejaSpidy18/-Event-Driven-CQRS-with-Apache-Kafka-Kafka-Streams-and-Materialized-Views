package com.example.command.service;

import com.example.command.dto.CreateProductRequest;
import com.example.command.entity.Product;
import com.example.command.event.EventEnvelope;
import com.example.command.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productTopic;

    public ProductService(ProductRepository productRepository,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          @Value("${app.topics.product-events}") String productTopic) {
        this.productRepository = productRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.productTopic = productTopic;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setCategory(request.category());
        product.setPrice(request.price());

        Product saved = productRepository.save(product);
        kafkaTemplate.send(productTopic, saved.getId().toString(), new EventEnvelope<>("ProductCreated", saved));
        return saved;
    }
}
