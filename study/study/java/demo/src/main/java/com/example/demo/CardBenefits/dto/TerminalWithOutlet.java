package com.example.demo.CardBenefits.dto;

import com.example.demo.CardBenefits.entity.Outlet;
import lombok.Data;

import java.util.ArrayList;

@Data
public class TerminalWithOutlet {
    public String terminalId;
    public String terminalName;
    public ArrayList<Outlet> outlets;
}
