package com.innowise.exception;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ErrorResponse error = new ErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED, 
                "UNAUTHORIZED", 
                "Authentication failed or token is missing/invalid.", 
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, 
                       AccessDeniedException accessDeniedException) throws IOException {
                       
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ErrorResponse error = new ErrorResponse(
                HttpServletResponse.SC_FORBIDDEN, 
                "ACCESS_DENIED", 
                "You do not have the required permissions for this resource.", 
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}