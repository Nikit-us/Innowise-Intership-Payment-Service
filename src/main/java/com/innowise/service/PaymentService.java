package com.innowise.service;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface PaymentService {
    ResponsePaymentDto createPayment(RequestPaymentDto dto);

    List<ResponsePaymentDto> getPaymentsByParameters(Long userId, Long orderId, Set<PaymentStatus> paymentStatus);

    Double getTotalSum(LocalDateTime start, LocalDateTime end);
}
