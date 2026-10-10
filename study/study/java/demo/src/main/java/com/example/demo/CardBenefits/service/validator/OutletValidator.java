package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.entity.Airport;
import com.example.demo.CardBenefits.entity.Terminal;
import com.example.demo.CardBenefits.entity.Outlet;
import com.example.demo.CardBenefits.service.DataStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OutletValidator implements ValidationStrategy {
    private final DataStoreService dataStoreService;

    @Autowired
    public OutletValidator(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    public void validate(ActivatePrivilegeRequest request) throws ValidationException {
        Airport airport = dataStoreService.getAirportByCode(request.getDepartureAirport());
        if (airport == null) throw new InvalidAirportException();
        Terminal terminal = airport.getTerminals().stream()
                .filter(t -> t.getName().equalsIgnoreCase(request.getTerminal()))
                .findFirst().orElse(null);
        if (terminal == null) throw new InvalidTerminalException();
        boolean found = terminal.getOutlets().stream()
                .anyMatch(o -> o.getId().equals(request.getOutletId()));
        if (!found) throw new OutletNotFoundException();
    }
} 