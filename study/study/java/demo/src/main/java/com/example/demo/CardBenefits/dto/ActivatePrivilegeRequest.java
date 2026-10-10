package com.example.demo.CardBenefits.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivatePrivilegeRequest {
    private String userId;
    private String flightNumber;
    private String departureAirport;
    private String terminal;
    private LocalDateTime departureTime;
    private String outletId;
    private int transactionAmount;
}
