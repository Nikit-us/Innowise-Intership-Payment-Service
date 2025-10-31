package com.innowise.integration.service.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.integration.AbstractIntegrationTest;
import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;
import com.innowise.repository.PaymentRepository;
import com.innowise.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentServiceImplIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private PaymentService paymentService;

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

    @Nested
    class CreatePayment {

        static Stream<Arguments> createPaymentArguments() {
            return Stream.of(
                    Arguments.of(new RequestPaymentDto(1L, 3L, 319.59), "1", PaymentStatus.FAILED),
                    Arguments.of(new RequestPaymentDto(2L, 5L, 500.00), "2", PaymentStatus.SUCCESS),
                    Arguments.of(new RequestPaymentDto(10L, 20L, 1000.50), "100", PaymentStatus.SUCCESS),
                    Arguments.of(new RequestPaymentDto(15L, 25L, 99.99), "999", PaymentStatus.FAILED)
            );
        }

        @ParameterizedTest
        @MethodSource("createPaymentArguments")
        void WhenValidDtoAndRandomNumber_ThenSavePaymentAndSendMessage(RequestPaymentDto requestDto, String randomNumber, PaymentStatus expectedStatus) {
            stubFor(get(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(randomNumber)));

            ResponsePaymentDto result = paymentService.createPayment(requestDto);

            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> assertThat(result.orderId()).isEqualTo(requestDto.orderId()),
                    () -> assertThat(result.userId()).isEqualTo(requestDto.userId()),
                    () -> assertThat(result.paymentAmount()).isEqualTo(requestDto.paymentAmount()),
                    () -> assertThat(result.status()).isEqualTo(expectedStatus),
                    () -> assertThat(result.id()).isNotNull(),
                    () -> assertThat(result.date()).isNotNull()
            );

            List<Payment> savedPayments = paymentRepository.findByOrderId(requestDto.orderId());
            assertThat(savedPayments).hasSize(1);

            Payment savedPayment = savedPayments.getFirst();
            assertAll(
                    () -> assertThat(savedPayment.getOrderId()).isEqualTo(String.valueOf(requestDto.orderId())),
                    () -> assertThat(savedPayment.getUserId()).isEqualTo(String.valueOf(requestDto.userId())),
                    () -> assertThat(savedPayment.getPaymentAmount()).isEqualTo(requestDto.paymentAmount()),
                    () -> assertThat(savedPayment.getStatus()).isEqualTo(expectedStatus),
                    () -> assertThat(savedPayment.getDate()).isNotNull()
            );
        }
    }

    @Nested
    class GetPaymentsByParameters {

        @Test
        void WhenUserIdProvided_ThenReturnPaymentsByUserId() {
            Payment p1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, LocalDateTime.now(), 100.0);
            Payment p2 = new Payment(null, "101", "200", PaymentStatus.FAILED, LocalDateTime.now(), 200.0);
            Payment p3 = new Payment(null, "102", "300", PaymentStatus.SUCCESS, LocalDateTime.now(), 150.0);
            List.of(p1, p2, p3).forEach(paymentRepository::save);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(200L, null, null);

            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result).extracting(ResponsePaymentDto::userId).containsOnly(200L)
            );
        }

        @Test
        void WhenOrderIdProvided_ThenReturnPaymentsByOrderId() {
            Payment p1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, LocalDateTime.now(), 100.0);
            Payment p2 = new Payment(null, "100", "201", PaymentStatus.FAILED, LocalDateTime.now(), 200.0);
            Payment p3 = new Payment(null, "101", "300", PaymentStatus.SUCCESS, LocalDateTime.now(), 150.0);
            List.of(p1, p2, p3).forEach(paymentRepository::save);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, 100L, null);

            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result).extracting(ResponsePaymentDto::orderId).containsOnly(100L)
            );
        }

        @Test
        void WhenPaymentStatusesProvided_ThenReturnPaymentsByStatuses() {
            Payment p1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, LocalDateTime.now(), 100.0);
            Payment p2 = new Payment(null, "101", "201", PaymentStatus.FAILED, LocalDateTime.now(), 200.0);
            Payment p3 = new Payment(null, "102", "300", PaymentStatus.SUCCESS, LocalDateTime.now(), 150.0);
            Payment p4 = new Payment(null, "103", "301", PaymentStatus.PENDING, LocalDateTime.now(), 180.0);
            List.of(p1, p2, p3, p4).forEach(paymentRepository::save);
            Set<PaymentStatus> statuses = Set.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, statuses);

            assertAll(
                    () -> assertThat(result).hasSize(3),
                    () -> assertThat(result).extracting(ResponsePaymentDto::status)
                            .allMatch(statuses::contains)
            );
        }

        @Test
        void WhenMultipleParametersProvided_ThenReturnCombinedResults() {
            Payment p1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, LocalDateTime.now(), 100.0);
            Payment p2 = new Payment(null, "100", "201", PaymentStatus.FAILED, LocalDateTime.now(), 200.0);
            Payment p3 = new Payment(null, "101", "300", PaymentStatus.SUCCESS, LocalDateTime.now(), 150.0);
            Payment p4 = new Payment(null, "102", "200", PaymentStatus.PENDING, LocalDateTime.now(), 180.0);
            Payment p5 = new Payment(null, "101", "301", PaymentStatus.FAILED, LocalDateTime.now(), 190.0);
            List.of(p1, p2, p3, p4, p5).forEach(paymentRepository::save);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(
                    200L, null, Set.of(PaymentStatus.SUCCESS)
            );

            assertThat(result).hasSize(4);
        }

        @Test
        void WhenNoParametersProvided_ThenReturnEmptyList() {
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, null);

            assertThat(result).isEmpty();
        }
    }
}
