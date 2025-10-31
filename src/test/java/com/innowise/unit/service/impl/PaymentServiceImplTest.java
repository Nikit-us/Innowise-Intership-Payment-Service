package com.innowise.service.impl;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.feing.RandomOrgClient;
import com.innowise.kafka.KafkaSender;
import com.innowise.mapper.PaymentMapper;
import com.innowise.mapper.PaymentMapperImpl;
import com.innowise.model.Payment;
import com.innowise.model.PaymentStatus;
import com.innowise.repository.PaymentRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {
    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private PaymentMapper paymentMapper = new PaymentMapperImpl();

    @Mock
    private RandomOrgClient randomOrgClient;

    @Spy
    private Clock clock = Clock.fixed(
            Instant.parse("2025-01-01T12:00:00Z"),
            ZoneId.of("UTC")
    );

    @Mock
    private KafkaSender kafkaSender;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Nested
    class CreatePayment {
        static Stream<Arguments> createPaymentArguments() {
            return Stream.of(
                    Arguments.of(
                            new RequestPaymentDto(1L, 3L, 319.59), "1", PaymentStatus.FAILED
                    ),
                    Arguments.of(
                            new RequestPaymentDto(2L, 5L, 500.00), "2", PaymentStatus.SUCCESS
                    ),
                    Arguments.of(
                            new RequestPaymentDto(10L, 20L, 1000.50), "100", PaymentStatus.SUCCESS
                    ),
                    Arguments.of(
                            new RequestPaymentDto(15L, 25L, 99.99), "999", PaymentStatus.FAILED
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("createPaymentArguments")
        void WhenValidDto_ThenSaveData(RequestPaymentDto testPayment, String randomNumber, PaymentStatus expectedStatus) {
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(randomOrgClient.getRandomNumber()).thenReturn(randomNumber);

            ResponsePaymentDto savedPayment = paymentService.createPayment(testPayment);

            assertAll(
                    () -> assertThat(savedPayment.date()).isEqualTo(LocalDateTime.now(clock)),
                    () -> assertThat(savedPayment.status()).isEqualTo(expectedStatus),
                    () -> assertThat(savedPayment.orderId()).isEqualTo(testPayment.orderId()),
                    () -> assertThat(savedPayment.userId()).isEqualTo(testPayment.userId()),
                    () -> assertThat(savedPayment.paymentAmount()).isEqualTo(testPayment.paymentAmount())
            );
        }


        @ParameterizedTest
        @MethodSource("createPaymentArguments")
        void WhenCreatePayment_ThenKafkaMessageIsSent(RequestPaymentDto testPayment, String randomNumber) {
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(randomOrgClient.getRandomNumber()).thenReturn(randomNumber);

            ResponsePaymentDto result = paymentService.createPayment(testPayment);

            ArgumentCaptor<ResponsePaymentDto> messageCaptor = ArgumentCaptor.forClass(ResponsePaymentDto.class);
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

            verify(kafkaSender, times(1)).sendPayment(messageCaptor.capture(), topicCaptor.capture());

            assertAll(
                    () -> assertThat(topicCaptor.getValue()).isEqualTo("CREATE_PAYMENT"),
                    () -> assertThat(messageCaptor.getValue()).isEqualTo(result)
            );
        }
    }

    @Nested
    class GetPaymentsByParameters {
        static Stream<Arguments> userIdProvider() {
            return Stream.of(
                    Arguments.of(
                            5L,
                            List.of(
                                    createPayment("1", "10", "5", PaymentStatus.SUCCESS, 100.0),
                                    createPayment("2", "11", "5", PaymentStatus.FAILED, 200.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("userIdProvider")
        void WhenUserIdProvided_ThenReturnPaymentsByUserId(Long userId, List<Payment> payments) {
            when(paymentRepository.findByUserId(userId)).thenReturn(payments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(userId, null, null);

            assertAll(
                    () -> assertThat(result)
                            .isNotEmpty()
                            .allMatch(p -> p.userId().equals(userId)),
                    () -> verify(paymentRepository, times(1)).findByUserId(userId),
                    () -> verify(paymentRepository, never()).findByOrderId(any()),
                    () -> verify(paymentRepository, never()).findByStatuses(any())
            );
        }

        static Stream<Arguments> orderIdProvider() {
            return Stream.of(
                    Arguments.of(
                            10L,
                            List.of(
                                    createPayment("1", "10", "5", PaymentStatus.SUCCESS, 100.0),
                                    createPayment("2", "10", "6", PaymentStatus.SUCCESS, 150.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("orderIdProvider")
        void WhenOrderIdProvided_ThenReturnPaymentsByOrderId(Long orderId, List<Payment> payments) {
            when(paymentRepository.findByOrderId(orderId)).thenReturn(payments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, orderId, null);

            assertAll(
                    () -> assertThat(result).hasSize(payments.size()),
                    () -> assertThat(result).allMatch(p -> p.orderId().equals(orderId)),
                    () -> verify(paymentRepository, times(1)).findByOrderId(orderId),
                    () -> verify(paymentRepository, never()).findByUserId(any()),
                    () -> verify(paymentRepository, never()).findByStatuses(any())
            );
        }

        static Stream<Arguments> statusProvider() {
            return Stream.of(
                    Arguments.of(
                            Set.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING),
                            List.of(
                                    createPayment("1", "10", "5", PaymentStatus.SUCCESS, 100.0),
                                    createPayment("2", "11", "6", PaymentStatus.PENDING, 200.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("statusProvider")
        void WhenPaymentStatusProvided_ThenReturnPaymentsByStatus(Set<PaymentStatus> statuses, List<Payment> payments) {
            when(paymentRepository.findByStatuses(statuses)).thenReturn(payments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, statuses);

            assertAll(
                    () -> assertThat(result).hasSize(payments.size()),
                    () -> assertThat(result).allMatch(p -> statuses.contains(p.status())),
                    () -> verify(paymentRepository, times(1)).findByStatuses(statuses),
                    () -> verify(paymentRepository, never()).findByUserId(any()),
                    () -> verify(paymentRepository, never()).findByOrderId(any())
            );
        }

        static Stream<Arguments> multipleParamsProvider() {
            return Stream.of(
                    Arguments.of(
                            5L,
                            10L,
                            Set.of(PaymentStatus.SUCCESS),
                            List.of(
                                    createPayment("1", "10", "5", PaymentStatus.SUCCESS, 100.0)
                            ),
                            List.of(
                                    createPayment("2", "10", "6", PaymentStatus.SUCCESS, 150.0)
                            ),
                            List.of(
                                    createPayment("3", "11", "7", PaymentStatus.SUCCESS, 200.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("multipleParamsProvider")
        void WhenMultipleParametersProvided_ThenReturnCombinedResults(
                Long userId,
                Long orderId,
                Set<PaymentStatus> statuses,
                List<Payment> userPayments,
                List<Payment> orderPayments,
                List<Payment> statusPayments
        ) {
            when(paymentRepository.findByUserId(userId)).thenReturn(userPayments);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(orderPayments);
            when(paymentRepository.findByStatuses(statuses)).thenReturn(statusPayments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(userId, orderId, statuses);

            assertAll(
                    () -> assertThat(result).hasSize(userPayments.size() + orderPayments.size() + statusPayments.size()),
                    () -> verify(paymentRepository, times(1)).findByUserId(userId),
                    () -> verify(paymentRepository, times(1)).findByOrderId(orderId),
                    () -> verify(paymentRepository, times(1)).findByStatuses(statuses)
            );
        }

        static Stream<Arguments> emptyParamsProvider() {
            return Stream.of(Arguments.of());
        }

        @ParameterizedTest
        @MethodSource("emptyParamsProvider")
        void WhenNoParametersProvided_ThenReturnEmptyList() {
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, null);

            assertAll(
                    () -> assertThat(result).isEmpty(),
                    () -> verify(paymentRepository, never()).findByUserId(any()),
                    () -> verify(paymentRepository, never()).findByOrderId(any()),
                    () -> verify(paymentRepository, never()).findByStatuses(any())
            );
        }

        static Stream<Arguments> userNotFoundProvider() {
            return Stream.of(Arguments.of(999L, List.of()));
        }

        @ParameterizedTest
        @MethodSource("userNotFoundProvider")
        void WhenUserIdProvidedButNoPaymentsFound_ThenReturnEmptyList(Long userId, List<Payment> emptyList) {
            when(paymentRepository.findByUserId(userId)).thenReturn(emptyList);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(userId, null, null);

            assertAll(
                    () -> assertThat(result).isEmpty(),
                    () -> verify(paymentRepository, times(1)).findByUserId(userId)
            );
        }

        static Stream<Arguments> allStatusesProvider() {
            return Stream.of(
                    Arguments.of(
                            Set.of(
                                    PaymentStatus.SUCCESS,
                                    PaymentStatus.FAILED,
                                    PaymentStatus.PENDING,
                                    PaymentStatus.CANCELLED
                            ),
                            List.of(
                                    createPayment("1", "10", "5", PaymentStatus.SUCCESS, 100.0),
                                    createPayment("2", "11", "6", PaymentStatus.FAILED, 200.0),
                                    createPayment("3", "12", "7", PaymentStatus.PENDING, 300.0),
                                    createPayment("4", "13", "8", PaymentStatus.CANCELLED, 400.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("allStatusesProvider")
        void WhenAllStatusesProvided_ThenReturnAllMatchingPayments(Set<PaymentStatus> allStatuses, List<Payment> payments) {
            when(paymentRepository.findByStatuses(allStatuses)).thenReturn(payments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, allStatuses);

            assertAll(
                    () -> assertThat(result)
                            .isNotNull()
                            .hasSize(payments.size()),
                    () -> assertThat(result)
                            .extracting(ResponsePaymentDto::status)
                            .containsExactlyInAnyOrderElementsOf(allStatuses)
            );
        }

        static Stream<Arguments> orderAndUserProvider() {
            return Stream.of(
                    Arguments.of(
                            5L,
                            10L,
                            List.of(
                                    createPayment("1", "9", "5", PaymentStatus.SUCCESS, 100.0),
                                    createPayment("2", "10", "5", PaymentStatus.FAILED, 200.0)
                            ),
                            List.of(
                                    createPayment("3", "10", "6", PaymentStatus.SUCCESS, 150.0)
                            )
                    )
            );
        }

        @ParameterizedTest
        @MethodSource("orderAndUserProvider")
        void WhenOrderIdAndUserIdProvided_ThenReturnCombinedResults(
                Long userId, Long orderId, List<Payment> userPayments, List<Payment> orderPayments
        ) {
            when(paymentRepository.findByUserId(userId)).thenReturn(userPayments);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(orderPayments);

            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(userId, orderId, null);

            assertAll(
                    () -> assertThat(result).hasSize(3),
                    () -> verify(paymentRepository, times(1)).findByUserId(userId),
                    () -> verify(paymentRepository, times(1)).findByOrderId(orderId),
                    () -> verify(paymentRepository, never()).findByStatuses(any())
            );
        }

        private static Payment createPayment(String id, String orderId, String userId, PaymentStatus status, Double amount) {
            return new Payment(id, orderId, userId, status, LocalDateTime.now(), amount);
        }
    }
}