package com.example.demo.CardBenefits.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BoardingPass {
    private String id;
    private String userId;
    private String depTerminalId;
    private String arrivalTerminalId;
    private LocalDateTime depTime;
    private LocalDateTime arrivalTime;
    private String flightNumber;
}
