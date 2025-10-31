package com.innowise.integration.kafka;

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
    }

    static Stream<Arguments> paymentRequests() {
        return Stream.of(
                Arguments.of(new RequestPaymentDto(1L, 3L, 319.59)),
                Arguments.of(new RequestPaymentDto(2L, 5L, 500.00)),
                Arguments.of(new RequestPaymentDto(10L, 20L, 1000.50))
        );
    }

    @ParameterizedTest
    @MethodSource("paymentRequests")
    void whenMessageArrives_thenListenerCreatesPayment(RequestPaymentDto dto) {
        // Отправляем сообщение в топик, который слушает KafkaListener
        kafkaTemplate.send("CREATE_ORDER", dto);

        // Ждём пока listener обработает событие
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    List<Payment> payments = paymentRepository.findByOrderId(dto.orderId());
                    assertThat(payments).hasSize(1);
                    Payment saved = payments.getFirst();

                    assertAll(
                            () -> assertThat(saved.getOrderId()).isEqualTo(String.valueOf(dto.orderId())),
                            () -> assertThat(saved.getUserId()).isEqualTo(String.valueOf(dto.userId())),
                            () -> assertThat(saved.getPaymentAmount()).isEqualTo(dto.paymentAmount()),
                            () -> assertThat(saved.getStatus()).isIn(PaymentStatus.SUCCESS, PaymentStatus.FAILED),
                            () -> assertThat(saved.getDate()).isNotNull()
                    );
                });
    }
}
