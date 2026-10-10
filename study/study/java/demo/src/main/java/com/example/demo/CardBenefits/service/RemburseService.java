package com.example.demo.CardBenefits.service;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.dto.ActivatePrivilegeResponse;
import com.example.demo.CardBenefits.entity.Offer;
import com.example.demo.CardBenefits.entity.Reimbursement;
import com.example.demo.CardBenefits.service.validator.ValidationChain;
import com.example.demo.CardBenefits.service.validator.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RemburseService {
    @Autowired
    private DataStoreService dataStoreService;
    @Autowired
    private OfferService offerService;
    @Autowired
    private ValidationChain validationChain;

    public ActivatePrivilegeResponse activatePrivilege(ActivatePrivilegeRequest request) {
        ActivatePrivilegeResponse response = new ActivatePrivilegeResponse();
        try {
            validationChain.validateAll(request);

            Offer offer = offerService.getOfferByAirportCode(request.getDepartureAirport());
            int originalAmount = request.getTransactionAmount();
            int reimbursementAmount = (originalAmount * offer.getReimbursementPercentage()) / 100;
            if (reimbursementAmount > offer.getMaxReimbursementAmount()) {
                reimbursementAmount = offer.getMaxReimbursementAmount();
            }
            boolean capApplied = reimbursementAmount < originalAmount;

            Reimbursement reimbursement = new Reimbursement();
            reimbursement.setId(UUID.randomUUID().toString());
            reimbursement.setUserId(request.getUserId());
            reimbursement.setOutletId(request.getOutletId());
            reimbursement.setBoardingPassId(UUID.randomUUID().toString());
            reimbursement.setOriginalAmount(originalAmount);
            reimbursement.setReimbursedAmount(reimbursementAmount);
            reimbursement.setCapApplied(capApplied);
            reimbursement.setTransactionTime(LocalDateTime.now());
            reimbursement.setNextPrivilegeAvailableTime(LocalDateTime.now().plusHours(2));
            dataStoreService.addReimbursement(reimbursement);

            response.setTransactionId(reimbursement.getId());
            response.setOriginalAmount(originalAmount);
            response.setFinalReimbursement(reimbursementAmount);
            response.setCapApplied(capApplied);
            response.setNextPrivilegeAvailableTime(reimbursement.getNextPrivilegeAvailableTime());
            response.setStatus("SUCCESS");
        } catch (ValidationException e) {
            response.setStatus(e.getCode());
        } catch (Exception e) {
            response.setStatus("ERROR: " + e.getMessage());
        }
        return response;
    }

    public List<Reimbursement> getReimbursementsByUser(String userId) {
        return dataStoreService.getReimbursementsByUser(userId);
    }

    public List<Reimbursement> getAllReimbursements() {
        return dataStoreService.getAllReimbursements();
    }
}
