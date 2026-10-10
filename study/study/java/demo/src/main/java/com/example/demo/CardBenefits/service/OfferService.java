package com.example.demo.CardBenefits.service;

import com.example.demo.CardBenefits.entity.Offer;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class OfferService {
    private final Map<String, Offer> offerByAirportCode = new HashMap<>();

    public OfferService() {
        Offer delhiOffer = new Offer();
        delhiOffer.setId("OFFER_DEL");
        delhiOffer.setAirportCode("DEL");
        delhiOffer.setReimbursementPercentage(100);
        delhiOffer.setMaxReimbursementAmount(2000);
        offerByAirportCode.put("DEL", delhiOffer);

        Offer mumbaiOffer = new Offer();
        mumbaiOffer.setId("OFFER_BOM");
        mumbaiOffer.setAirportCode("BOM");
        mumbaiOffer.setReimbursementPercentage(80);
        mumbaiOffer.setMaxReimbursementAmount(3000);
        offerByAirportCode.put("BOM", mumbaiOffer);
    }

    public Offer getOfferByAirportCode(String airportCode) {
        return offerByAirportCode.get(airportCode);
    }
} 