package com.innowise.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
                    .waitingFor(Wait.forListeningPort());

    @Container
    protected static final KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka-native:3.8.0")
                    .waitingFor(Wait.forListeningPort());

    @Container
    protected static final WireMockContainer wireMockContainer = new WireMockContainer("wiremock/wiremock:3.13.1-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("random.org.url", wireMockContainer::getBaseUrl);
    }
}
