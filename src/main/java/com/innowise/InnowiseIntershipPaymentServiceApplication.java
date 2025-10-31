package com.innowise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class InnowiseIntershipPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InnowiseIntershipPaymentServiceApplication.class, args);
    }
}
