package com.innowise.mapper;

import com.innowise.dto.RequestPaymentDto;
import com.innowise.dto.ResponsePaymentDto;
import com.innowise.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "date", ignore = true)
    @Mapping(target = "status", ignore = true)
    Payment toPayment(RequestPaymentDto dto);

    ResponsePaymentDto toResponsePaymentDto(Payment payment);
    List<ResponsePaymentDto> toResponsePaymentDto(List<Payment> payments);
}
