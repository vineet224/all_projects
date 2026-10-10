package com.example.demo.CardBenefits.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Reimbursement {
    private String id;
    private String userId;
    private String outletId;
    private String boardingPassId;
    private int originalAmount;
    private int reimbursedAmount;
    private boolean capApplied;
    private LocalDateTime transactionTime;
    private LocalDateTime nextPrivilegeAvailableTime;
}
