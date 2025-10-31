package com.innowise.service.impl;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.feing.RandomOrgClient;
import com.innowise.kafka.KafkaSenderService;
import com.innowise.mapper.PaymentMapper;
import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;
import com.innowise.repository.PaymentRepository;
import com.innowise.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomOrgClient randomOrgClient;
    private final KafkaSenderService kafkaSenderService;
    private final Clock clock;

    @Override
    public ResponsePaymentDto createPayment(RequestPaymentDto dto) {
        Payment payment = paymentMapper.toPayment(dto);
        payment.setDate(LocalDateTime.now(clock));
        payment.setStatus(
                getRandomNumber() % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED
        );
        log.info("Creating payment with id {}", payment);
        ResponsePaymentDto savedPayment = paymentMapper.toResponsePaymentDto(paymentRepository.save(payment));

        kafkaSenderService.sendPayment(savedPayment, "CREATE_PAYMENT");
        return savedPayment;
    }

    @Override
    public List<ResponsePaymentDto> getPaymentsByParameters(Long userId, Long orderId, Set<PaymentStatus> paymentStatus) {
        List<ResponsePaymentDto> payments = new ArrayList<>();
        if (userId != null) {
            payments.addAll(paymentMapper.toResponsePaymentDto(paymentRepository.findByUserId(userId)));
        }
        if (orderId != null) {
            payments.addAll(paymentMapper.toResponsePaymentDto(paymentRepository.findByOrderId(orderId)));
        }
        if (paymentStatus != null) {
            payments.addAll(paymentMapper.toResponsePaymentDto(paymentRepository.findByStatuses(paymentStatus)));
            log.info("Finding payments by statuses: {}", payments);
        }
        return payments;
    }

    private int getRandomNumber() {
        String randomNumber = randomOrgClient.getRandomNumber().trim();
        log.info("Random number: {}", randomNumber);
        return Integer.parseInt(randomNumber);
    }
}
