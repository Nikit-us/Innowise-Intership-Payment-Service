package com.innowise.integration.kafka;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.integration.AbstractIntegrationTest;
import com.innowise.kafka.KafkaSender;
import com.innowise.model.PaymentStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

class KafkaSenderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaSender kafkaSender;

    @Autowired
    private KafkaTemplate<String, ResponsePaymentDto> kafkaTemplate;

    private Consumer<String, ResponsePaymentDto> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        consumer = new KafkaConsumer<>(props, new StringDeserializer(),
                new JsonDeserializer<>(ResponsePaymentDto.class, false));
        consumer.subscribe(Collections.singleton("CREATE_PAYMENT"));

    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    static Stream<Arguments> paymentMessages() {
        return Stream.of(
                Arguments.of(new ResponsePaymentDto("1", 3L, 1L, PaymentStatus.SUCCESS, LocalDateTime.now(), 359.99)),
                Arguments.of(new ResponsePaymentDto("2", 5L, 2L, PaymentStatus.FAILED,  LocalDateTime.now(), 12.0))
        );
    }

    @ParameterizedTest
    @MethodSource("paymentMessages")
    void whenSendPayment_thenMessageAppearsInKafka(ResponsePaymentDto dto) {
        kafkaSender.sendPayment(dto, "CREATE_PAYMENT");

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
