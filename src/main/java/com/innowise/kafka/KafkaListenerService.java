package com.innowise.kafka;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaListenerService {
    private final PaymentService paymentService;

    @KafkaListener(topics = "CREATE_ORDER", groupId = "payment-service-group")
    void listenCreateOrder(RequestPaymentDto data) {
        log.info("Request data: {}", data);
        paymentService.createPayment(data);
    }
}