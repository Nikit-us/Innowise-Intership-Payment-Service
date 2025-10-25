package com.innowise.repository;

import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findByUserId(Long userId);
    List<Payment> findByStatuses(PaymentStatus... statuses);
    double getTotalSumOfDatePeriod(LocalDateTime startDate, LocalDateTime endDate);
}
