package com.innowise.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RequestPaymentDto(
        @NotNull(message = "orderId cannot be null")
        @Positive(message = "orderId must be greater than 0")
        Long orderId,

        @NotNull(message = "userId cannot be null")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        @NotNull(message = "paymentAmount cannot be null")
        @Positive(message = "paymentAmount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "paymentAmount must have up to 2 decimal places")
        Double paymentAmount
) {
}
