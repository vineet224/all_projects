package com.example.demo.CardBenefits.entity;

import lombok.Data;
import java.util.List;

@Data
public class Terminal {
    private String id;
    private String name;
    private String airportId;
    private List<Outlet> outlets;
}
