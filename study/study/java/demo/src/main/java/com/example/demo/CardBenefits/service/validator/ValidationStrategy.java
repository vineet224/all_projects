package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;

public interface ValidationStrategy {
    void validate(ActivatePrivilegeRequest request) throws ValidationException;
} 