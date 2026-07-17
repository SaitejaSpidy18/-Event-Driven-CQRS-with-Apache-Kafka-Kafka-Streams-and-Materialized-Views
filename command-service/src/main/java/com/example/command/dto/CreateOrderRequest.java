package com.example.command.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Integer customerId,
        @NotEmpty List<@Valid OrderItemRequest> items
) {}
