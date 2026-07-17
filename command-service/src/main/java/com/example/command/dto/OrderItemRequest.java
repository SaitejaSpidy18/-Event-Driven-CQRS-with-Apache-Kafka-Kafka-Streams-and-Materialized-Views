package com.example.command.dto;

import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull Integer quantity,
        @NotNull Double price
) {}
