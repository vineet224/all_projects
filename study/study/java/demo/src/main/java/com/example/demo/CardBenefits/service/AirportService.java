package com.example.demo.CardBenefits.service;

import com.example.demo.CardBenefits.dto.AirportDetail;
import com.example.demo.CardBenefits.dto.TerminalWithOutlet;
import com.example.demo.CardBenefits.entity.Airport;
import com.example.demo.CardBenefits.entity.Offer;
import com.example.demo.CardBenefits.entity.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AirportService {
    
    @Autowired
    private DataStoreService dataStoreService;

    @Autowired
    private OfferService offerService;

    public AirportDetail getAirportDetailByCode(String airportCode) {
        Airport airport = dataStoreService.getAirportByCode(airportCode);
        if (airport == null) {
            return null;
        }

        Offer offerForThisAirPort = offerService.getOfferByAirportCode(airportCode);
        AirportDetail airportDetail = new AirportDetail();
        airportDetail.setAirportName(airport.getName());
        airportDetail.setPercentageDiscount(offerForThisAirPort.getReimbursementPercentage());
        airportDetail.setAmount(offerForThisAirPort.getMaxReimbursementAmount());
        airportDetail.setTerminals(new ArrayList<>());

        for (Terminal terminal : airport.getTerminals()) {
            TerminalWithOutlet terminalWithOutlet = new TerminalWithOutlet();
            terminalWithOutlet.setTerminalId(terminal.getId());
            terminalWithOutlet.setTerminalName(terminal.getName());
            terminalWithOutlet.setOutlets(new ArrayList<>(terminal.getOutlets()));
            airportDetail.getTerminals().add(terminalWithOutlet);
        }

        return airportDetail;
    }

    public List<AirportDetail> getAirportDetailByCity(String city) {
        List<AirportDetail> airportDetails = new ArrayList<>();

        for (Airport airport : dataStoreService.getAllAirports()) {
            if (airport.getCity().equalsIgnoreCase(city)) {
                airportDetails.add(getAirportDetailByCode(airport.getAirportCode()));
            }
        }

        return airportDetails;
    }
}
