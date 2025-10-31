package com.innowise.integration.kafka;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.dto.RequestPaymentDto;
import com.innowise.integration.AbstractIntegrationTest;
import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;
import com.innowise.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaListenerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, RequestPaymentDto> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();
        WireMock.configureFor(
                wireMockContainer.getHost(),
                wireMockContainer.getPort()
        );
    }

    static Stream<Arguments> paymentRequests() {
        return Stream.of(
                Arguments.of(new RequestPaymentDto(1L, 3L, 319.59), "2", PaymentStatus.SUCCESS),
                Arguments.of(new RequestPaymentDto(2L, 5L, 500.00), "1", PaymentStatus.FAILED),
                Arguments.of(new RequestPaymentDto(10L, 20L, 1000.50), "43", PaymentStatus.FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("paymentRequests")
    void WhenMessageArrives_ThenListenerCreatesPayment(RequestPaymentDto dto, String randomNumber, PaymentStatus status) {
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(randomNumber)));

        kafkaTemplate.send("CREATE_ORDER", dto);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    List<Payment> payments = paymentRepository.findByOrderId(dto.orderId());
                    assertThat(payments).hasSize(1);
                    Payment saved = payments.getFirst();

                    assertAll(
                            () -> assertThat(saved.getOrderId()).isEqualTo(String.valueOf(dto.orderId())),
                            () -> assertThat(saved.getUserId()).isEqualTo(String.valueOf(dto.userId())),
                            () -> assertThat(saved.getPaymentAmount()).isEqualTo(dto.paymentAmount()),
                            () -> assertThat(saved.getStatus()).isEqualTo(status),
                            () -> assertThat(saved.getDate()).isNotNull()
                    );
                });
    }
}
