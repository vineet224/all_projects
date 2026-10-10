package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.entity.Airport;
import com.example.demo.CardBenefits.entity.Terminal;
import com.example.demo.CardBenefits.service.DataStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TerminalValidator implements ValidationStrategy {
    private final DataStoreService dataStoreService;

    @Autowired
    public TerminalValidator(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    public void validate(ActivatePrivilegeRequest request) throws ValidationException {
        Airport airport = dataStoreService.getAirportByCode(request.getDepartureAirport());
        if (airport == null) throw new InvalidAirportException();
        boolean found = airport.getTerminals().stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(request.getTerminal()));
        if (!found) throw new InvalidTerminalException();
    }
} 