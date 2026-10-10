package com.example.demo.CardBenefits.entity;
import lombok.Data;
import java.util.List;

@Data
public class Airport {
    private String id;
    private String airportCode;
    private String name;
    private String city;
    private String state;
    private List<Terminal> terminals;
}
