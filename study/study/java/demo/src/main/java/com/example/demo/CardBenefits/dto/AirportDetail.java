package com.example.demo.CardBenefits.dto;

import com.example.demo.CardBenefits.entity.Terminal;
import lombok.Data;

import java.util.ArrayList;

@Data
public class AirportDetail {
    private String airportName;
    private int percentageDiscount;
    private int amount;
    private ArrayList<TerminalWithOutlet> terminals;
}
