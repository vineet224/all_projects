package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(DemoApplication.class, args);
		System.out.println("=== Airport Privileges Management System Started ===");
		System.out.println("Available APIs:");
		System.out.println("1. GET /api/airports/{airportCode} - Get airport details");
		System.out.println("2. POST /api/activate-privilege - Activate privilege");
		System.out.println("3. GET /api/reports/reimbursements - Get reimbursement reports");
		System.out.println("4. GET /api/reports/reimbursements/{userId} - Get user reimbursements");
	}
}
