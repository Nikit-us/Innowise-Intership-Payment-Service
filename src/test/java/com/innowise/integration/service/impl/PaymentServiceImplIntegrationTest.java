package com.innowise.integration.service.impl;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Импорты для Kafka Consumer
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentServiceImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, ResponsePaymentDto> kafkaTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Nested
    class CreatePayment {
        @Test
        void WhenValidDtoAndRandomNumberIsEven_ThenSavePaymentAndSendMessageAndReturnSuccess() {
            // Arrange
            Long orderId = 100L;
            Long userId = 200L;
            Double amount = 250.75;
            RequestPaymentDto requestDto = new RequestPaymentDto(orderId, userId, amount);

            String randomNumberResponse = "42"; // Четное -> SUCCESS
            stubFor(get(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(randomNumberResponse)));

            // Act
            ResponsePaymentDto result = paymentService.createPayment(requestDto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.paymentAmount()).isEqualTo(amount);
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(result.date()).isNotNull();
            assertThat(result.id()).isNotNull();

            // Проверка сохранения в MongoDB
            List<Payment> savedPayments = paymentRepository.findByOrderId(orderId);
            assertThat(savedPayments).hasSize(1);
            Payment savedPayment = savedPayments.get(0);
            assertThat(savedPayment.getOrderId()).isEqualTo(String.valueOf(orderId));
            assertThat(savedPayment.getUserId()).isEqualTo(String.valueOf(userId));
            assertThat(savedPayment.getPaymentAmount()).isEqualTo(amount);
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(savedPayment.getDate()).isNotNull();

            // Проверка отправки сообщения в Kafka
            assertKafkaMessageReceived(result);
        }

        @Test
        void WhenValidDtoAndRandomNumberIsOdd_ThenSavePaymentAndSendMessageAndReturnFailed() {
             // Arrange
            Long orderId = 101L;
            Long userId = 201L;
            Double amount = 150.00;
            RequestPaymentDto requestDto = new RequestPaymentDto(orderId, userId, amount);

            String randomNumberResponse = "13"; // Нечетное -> FAILED
            stubFor(get(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(randomNumberResponse)));

            // Act
            ResponsePaymentDto result = paymentService.createPayment(requestDto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.paymentAmount()).isEqualTo(amount);
            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED); // Проверяем FAILED
            assertThat(result.date()).isNotNull();
            assertThat(result.id()).isNotNull();

            // Проверка сохранения в MongoDB
            List<Payment> savedPayments = paymentRepository.findByOrderId(orderId);
            assertThat(savedPayments).hasSize(1);
            Payment savedPayment = savedPayments.get(0);
            assertThat(savedPayment.getOrderId()).isEqualTo(String.valueOf(orderId));
            assertThat(savedPayment.getUserId()).isEqualTo(String.valueOf(userId));
            assertThat(savedPayment.getPaymentAmount()).isEqualTo(amount);
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED); // Проверяем FAILED
            assertThat(savedPayment.getDate()).isNotNull();

            // Проверка отправки сообщения в Kafka
            assertKafkaMessageReceived(result);
        }
    }

    @Nested
    class GetPaymentsByParameters {
        @Test
        void WhenUserIdProvided_ThenReturnPaymentsByUserId() {
            // Arrange
            Payment payment1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, null, 100.0);
            Payment payment2 = new Payment(null, "101", "200", PaymentStatus.FAILED, null, 200.0);
            Payment payment3 = new Payment(null, "102", "300", PaymentStatus.SUCCESS, null, 150.0);

            paymentRepository.save(payment1);
            paymentRepository.save(payment2);
            paymentRepository.save(payment3);

            // Act
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(200L, null, null);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result)
                .extracting(ResponsePaymentDto::userId)
                .allMatch(id -> id.equals(200L));
        }

        @Test
        void WhenOrderIdProvided_ThenReturnPaymentsByOrderId() {
             // Arrange
            Payment payment1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, null, 100.0);
            Payment payment2 = new Payment(null, "100", "201", PaymentStatus.FAILED, null, 200.0);
            Payment payment3 = new Payment(null, "101", "300", PaymentStatus.SUCCESS, null, 150.0);

            paymentRepository.save(payment1);
            paymentRepository.save(payment2);
            paymentRepository.save(payment3);

            // Act
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, 100L, null);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result)
                .extracting(ResponsePaymentDto::orderId)
                .allMatch(id -> id.equals(100L));
        }

         @Test
        void WhenPaymentStatusesProvided_ThenReturnPaymentsByStatuses() {
             // Arrange
            Payment payment1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, null, 100.0);
            Payment payment2 = new Payment(null, "101", "201", PaymentStatus.FAILED, null, 200.0);
            Payment payment3 = new Payment(null, "102", "300", PaymentStatus.SUCCESS, null, 150.0);
            Payment payment4 = new Payment(null, "103", "301", PaymentStatus.PENDING, null, 180.0);

            paymentRepository.save(payment1);
            paymentRepository.save(payment2);
            paymentRepository.save(payment3);
            paymentRepository.save(payment4);

            Set<PaymentStatus> statuses = Set.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING);

            // Act
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, statuses);

            // Assert
            assertThat(result).hasSize(3); // payment1, payment3, payment4
            assertThat(result)
                .extracting(ResponsePaymentDto::status)
                .allMatch(status -> statuses.contains(status));
        }

        @Test
        void WhenMultipleParametersProvided_ThenReturnCombinedResults() {
            // Arrange
            Payment payment1 = new Payment(null, "100", "200", PaymentStatus.SUCCESS, null, 100.0); // userId=200
            Payment payment2 = new Payment(null, "100", "201", PaymentStatus.FAILED, null, 200.0); // orderId=100
            Payment payment3 = new Payment(null, "101", "300", PaymentStatus.SUCCESS, null, 150.0); // status=SUCCESS
            Payment payment4 = new Payment(null, "102", "200", PaymentStatus.PENDING, null, 180.0); // userId=200
            Payment payment5 = new Payment(null, "101", "301", PaymentStatus.FAILED, null, 190.0); // status=FAILED

            paymentRepository.save(payment1);
            paymentRepository.save(payment2);
            paymentRepository.save(payment3);
            paymentRepository.save(payment4);
            paymentRepository.save(payment5);

            // Act
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(200L, null, Set.of(PaymentStatus.SUCCESS));

            // Assert
            // findByUserId(200L) -> [payment1, payment4] (2 платежа)
            // findByStatuses([SUCCESS]) -> [payment1, payment3] (2 платежа)
            // Результат: [payment1, payment4, payment1, payment3] -> 4 элемента (с дубликатами)
            assertThat(result).hasSize(4);
        }

        @Test
        void WhenNoParametersProvided_ThenReturnEmptyList() {
            // Act
            List<ResponsePaymentDto> result = paymentService.getPaymentsByParameters(null, null, null);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // Вспомогательный метод для проверки сообщения в Kafka
    private void assertKafkaMessageReceived(ResponsePaymentDto expectedDto) {
        String group = "integration-test-consumer-group-create";
        java.util.Properties consumerProps = new java.util.Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<ResponsePaymentDto> jsonValueDeserializer = new JsonDeserializer<>(ResponsePaymentDto.class, true);
        jsonValueDeserializer.addTrustedPackages("*");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, jsonValueDeserializer);

        DefaultKafkaConsumerFactory<String, ResponsePaymentDto> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        Consumer<String, ResponsePaymentDto> consumer = cf.createConsumer();
        consumer.subscribe(Set.of("CREATE_PAYMENT"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            org.apache.kafka.clients.consumer.ConsumerRecords<String, ResponsePaymentDto> records = consumer.poll(Duration.ofMillis(100));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            boolean messageFound = false;
            for (ConsumerRecord<String, ResponsePaymentDto> record : records) {
                 ResponsePaymentDto receivedDto = record.value();
                 if (receivedDto != null &&
                     receivedDto.orderId().equals(expectedDto.orderId()) &&
                     receivedDto.userId().equals(expectedDto.userId()) &&
                     receivedDto.paymentAmount().equals(expectedDto.paymentAmount()) &&
                     receivedDto.status() == expectedDto.status()) {
                     messageFound = true;
                     break;
                 }
             }
             assertThat(messageFound).isTrue();
        });

        consumer.close();
    }
}