package com.example.demo.CardBenefits.service.validator;

public class ValidationException extends RuntimeException {
    private final String code;
    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}

class InvalidAirportException extends ValidationException {
    public InvalidAirportException() {
        super("INVALID_AIRPORT", "Invalid airport for privilege activation.");
    }
}

class InvalidTerminalException extends ValidationException {
    public InvalidTerminalException() {
        super("INVALID_TERMINAL", "Invalid terminal for privilege activation.");
    }
}

class TimeWindowViolationException extends ValidationException {
    public TimeWindowViolationException() {
        super("TIME_WINDOW_VIOLATION", "Privilege already used in the last 2 hours.");
    }
}

class OutletNotFoundException extends ValidationException {
    public OutletNotFoundException() {
        super("OUTLET_NOT_FOUND", "Outlet not found in the specified terminal.");
    }
}

class InvalidBoardingPassException extends ValidationException {
    public InvalidBoardingPassException() {
        super("INVALID_BOARDING_PASS", "Boarding pass is not valid for privilege activation.");
    }
} 