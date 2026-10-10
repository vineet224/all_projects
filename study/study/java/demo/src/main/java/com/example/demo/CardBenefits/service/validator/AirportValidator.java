package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.service.DataStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AirportValidator implements ValidationStrategy {
    private final DataStoreService dataStoreService;

    @Autowired
    public AirportValidator(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    public void validate(ActivatePrivilegeRequest request) throws ValidationException {
        if (dataStoreService.getAirportByCode(request.getDepartureAirport()) == null) {
            throw new InvalidAirportException();
        }
    }
} 