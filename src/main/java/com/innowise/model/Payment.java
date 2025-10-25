package com.innowise.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "payments")
@Getter
@Setter
@ToString
public class Payment {
    @Id
    private String id;

    @Field(name = "order_id")
    private String orderId;

    @Field(name = "user_id")
    private String userId;

    @Field(name = "status")
    private PaymentStatus status;

    @Field(name = "timestamp")
    private LocalDateTime date;

    @Field(name = "payment_amount")
    private Double paymentAmount;
}
