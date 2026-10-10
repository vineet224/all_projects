package com.example.demo.CardBenefits.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivatePrivilegeResponse {
    private String transactionId;
    private int originalAmount;
    private int finalReimbursement;
    private boolean capApplied;
    private LocalDateTime nextPrivilegeAvailableTime;
    private String status;
} 