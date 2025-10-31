package com.innowise.kafka;

import com.innowise.dto.ResponsePaymentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaSenderService {
    private final KafkaTemplate<String, ResponsePaymentDto> kafkaTemplate;

    public void sendPayment(ResponsePaymentDto message, String topicName) {
        log.info("Send message: " + message.toString());
        kafkaTemplate.send(topicName, message);
    }
}
