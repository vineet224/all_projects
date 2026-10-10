package com.example.demo.CardBenefits.entity;

import lombok.Data;

@Data
public class Outlet {
    private String id;
    private String name;
    private OutletTypeEnum type;
    private String terminalId;
}
