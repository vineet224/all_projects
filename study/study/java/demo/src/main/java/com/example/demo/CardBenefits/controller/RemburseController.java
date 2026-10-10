package com.example.demo.CardBenefits.controller;

import com.example.demo.CardBenefits.dto.AirportDetail;
import com.example.demo.CardBenefits.dto.ActivatePrivilegeRequest;
import com.example.demo.CardBenefits.dto.ActivatePrivilegeResponse;
import com.example.demo.CardBenefits.entity.Reimbursement;
import com.example.demo.CardBenefits.service.AirportService;
import com.example.demo.CardBenefits.service.RemburseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RemburseController {

    @Autowired
    private AirportService airportService;
    
    @Autowired
    private RemburseService remburseService;

    @GetMapping("/airports/{airportCode}")
    public ResponseEntity<AirportDetail> getAirportDetails(@PathVariable String airportCode) {
        AirportDetail airportDetail = airportService.getAirportDetailByCode(airportCode);
        if (airportDetail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(airportDetail);
    }

    @PostMapping("/activate-privilege")
    public ResponseEntity<ActivatePrivilegeResponse> activatePrivilege(@RequestBody ActivatePrivilegeRequest request) {
        ActivatePrivilegeResponse response = remburseService.activatePrivilege(request);
        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        }

        HttpStatus status;
        switch (response.getStatus()) {
            case "INVALID_AIRPORT":
            case "OUTLET_NOT_FOUND":
            case "INVALID_TERMINAL":
            case "TIME_WINDOW_VIOLATION":
            case "INVALID_BOARDING_PASS":
                status = HttpStatus.BAD_REQUEST;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(response);
    }


    @GetMapping("/reports/reimbursements")
    public ResponseEntity<List<Reimbursement>> getReimbursementReports() {
        List<Reimbursement> reimbursements = remburseService.getAllReimbursements();
        return ResponseEntity.ok(reimbursements);
    }
    

    @GetMapping("/reports/reimbursements/{userId}")
    public ResponseEntity<List<Reimbursement>> getUserReimbursements(@PathVariable String userId) {
        List<Reimbursement> userReimbursements = remburseService.getReimbursementsByUser(userId);
        return ResponseEntity.ok(userReimbursements);
    }
}


// Airport
// Terminal
// Outlet
// Validation
// Reumbrsements
