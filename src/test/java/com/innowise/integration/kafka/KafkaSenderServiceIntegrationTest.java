package com.innowise.integration.kafka;

import com.innowise.dto.ResponsePaymentDto;
import com.innowise.integration.AbstractIntegrationTest;
import com.innowise.integration.config.KafkaTestConsumerConfig;
import com.innowise.kafka.KafkaSenderService;
import com.innowise.model.PaymentStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

@Import(KafkaTestConsumerConfig.class)
class KafkaSenderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaSenderService kafkaSenderService;

    @Autowired
    private ConsumerFactory<String, ResponsePaymentDto> consumerFactory;

    private static Consumer<String, ResponsePaymentDto> consumer;

    @BeforeAll
    static void setup(@Autowired ConsumerFactory<String, ResponsePaymentDto> cf) {
        consumer = cf.createConsumer();
        consumer.subscribe(Collections.singleton("CREATE_PAYMENT"));
        KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
    }

    @AfterAll
    static void tearDown() {
        if (consumer != null) consumer.close();
    }

    static Stream<Arguments> paymentMessages() {
        return Stream.of(
                Arguments.of(new ResponsePaymentDto("1", 3L, 1L, PaymentStatus.SUCCESS, LocalDateTime.now(), 359.99)),
                Arguments.of(new ResponsePaymentDto("2", 5L, 2L, PaymentStatus.FAILED,  LocalDateTime.now(), 12.0))
        );
    }

    @ParameterizedTest
    @MethodSource("paymentMessages")
    void WhenSendPayment_ThenMessageAppearsInKafka(ResponsePaymentDto dto) {
        kafkaSenderService.sendPayment(dto, "CREATE_PAYMENT");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    ConsumerRecords<String, ResponsePaymentDto> records = consumer.poll(Duration.ofMillis(500));
                    assertThat(records.count()).isGreaterThan(0);

                    ResponsePaymentDto received = records.iterator().next().value();

                    assertAll(
                            () -> assertThat(received.orderId()).isEqualTo(dto.orderId()),
                            () -> assertThat(received.userId()).isEqualTo(dto.userId()),
                            () -> assertThat(received.paymentAmount()).isEqualTo(dto.paymentAmount()),
                            () -> assertThat(received.status()).isEqualTo(dto.status())
                    );
                });
    }
}
