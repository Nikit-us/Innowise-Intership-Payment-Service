package com.innowise.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    @Around("execution(* com.innowise.service..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("Starting method: {} with args: {}", methodName, args);

        Object proceed;
        try {
            proceed = joinPoint.proceed();
        } catch (Throwable e) {
            log.error("Exception in method: {} with message: {}", methodName, e.getMessage());
            throw e;
        }

        log.info("Finished method: {}. Result: {}", methodName, proceed);
        return proceed;
    }
}