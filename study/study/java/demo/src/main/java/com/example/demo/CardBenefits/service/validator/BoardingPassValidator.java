package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class BoardingPassValidator implements ValidationStrategy {
    @Override
    public void validate(ActivatePrivilegeRequest request) throws ValidationException {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntilDeparture = ChronoUnit.HOURS.between(now, request.getDepartureTime());
        if (hoursUntilDeparture < 0 || hoursUntilDeparture > 24) {
            throw new InvalidBoardingPassException();
        }
    }
} 