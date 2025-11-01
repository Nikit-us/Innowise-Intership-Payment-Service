package com.innowise.dto;

import com.innowise.model.PaymentStatus;

import java.time.LocalDateTime;

public record ResponsePaymentDto(
        String id,
        Long orderId,
        Long userId,
        PaymentStatus status,
        LocalDateTime date,
        Double paymentAmount
) {
}
