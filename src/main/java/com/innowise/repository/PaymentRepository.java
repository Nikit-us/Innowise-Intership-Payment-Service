package com.innowise.repository;

import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface PaymentRepository {
    Payment save(Payment payment);

    List<Payment> findByOrderId(Long orderId);
    List<Payment> findByUserId(Long userId);
    List<Payment> findByStatuses(Set<PaymentStatus> statuses);
    double getTotalSumOfDatePeriod(LocalDateTime startDate, LocalDateTime endDate);
}
