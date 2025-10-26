package com.innowise.controller;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.model.PaymentStatus;
import com.innowise.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ResponsePaymentDto> createPayment(@Valid @RequestBody RequestPaymentDto dto) {
        return ResponseEntity.ok(paymentService.createPayment(dto));
    }

    @GetMapping
    public ResponseEntity<List<ResponsePaymentDto>> getPaymentsByStatus(
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) Set<PaymentStatus> paymentStatus) {
        return ResponseEntity.ok(paymentService.getPaymentsByParameters(userId, orderId, paymentStatus));
    }
}