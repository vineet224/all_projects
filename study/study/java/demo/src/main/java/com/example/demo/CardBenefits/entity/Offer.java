package com.example.demo.CardBenefits.entity;

import lombok.Data;

@Data
public class Offer {
    private String id;
    private String airportCode;
    private int reimbursementPercentage;
    private int maxReimbursementAmount;
}
