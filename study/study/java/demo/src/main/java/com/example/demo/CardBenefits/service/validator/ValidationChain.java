package com.example.demo.CardBenefits.service.validator;

import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ValidationChain {
    private final List<ValidationStrategy> validators;

    @Autowired
    public ValidationChain(List<ValidationStrategy> validators) {
        this.validators = validators;
    }

    public void validateAll(ActivatePrivilegeRequest request) throws ValidationException {
        for (ValidationStrategy validator : validators) {
            validator.validate(request);
        }
    }
} 