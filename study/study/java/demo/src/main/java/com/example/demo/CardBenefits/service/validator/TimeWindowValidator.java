package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.entity.Reimbursement;
import com.example.demo.CardBenefits.service.DataStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TimeWindowValidator implements ValidationStrategy {
    private final DataStoreService dataStoreService;

    @Autowired
    public TimeWindowValidator(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    public void validate(ActivatePrivilegeRequest request) throws ValidationException {
        List<Reimbursement> userReimbursements = dataStoreService.getReimbursementsByUser(request.getUserId());
        LocalDateTime now = LocalDateTime.now();
        for (Reimbursement r : userReimbursements) {
            if (r.getNextPrivilegeAvailableTime() != null && now.isBefore(r.getNextPrivilegeAvailableTime())) {
                throw new TimeWindowViolationException();
            }
        }
    }
} 