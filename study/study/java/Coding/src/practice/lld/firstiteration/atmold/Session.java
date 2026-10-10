package practice.lld.firstiteration.atmold;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class Session {

    public final String cardNumber;
    public final String cvv;
    public final LocalDateTime startedAt;

    public LocalDateTime closedAt;

    private final AtmMachine atm;
    private final BankInterface bank;

    private boolean authenticated = false;
    private int failedPinAttempts = 0;
    private static final int MAX_PIN_ATTEMPTS = 3;
    private String pin; // stored only for the lifetime of the session in this demo

    public Session(AtmMachine atm, BankInterface bank, String cardNumber, String cvv) {
        this.atm = Objects.requireNonNull(atm);
        this.bank = Objects.requireNonNull(bank);
        this.cardNumber = Objects.requireNonNull(cardNumber);
        this.cvv = Objects.requireNonNull(cvv);
        this.startedAt = LocalDateTime.now();
    }

    public void authenticate(String pin) {
        ensureOpen();
        if (authenticated) return;

        if (failedPinAttempts >= MAX_PIN_ATTEMPTS) {
            throw new IllegalStateException("Card blocked for this session due to failed PIN attempts");
        }

        boolean ok = bank.validatePin(cardNumber, cvv, pin);
        if (!ok) {
            failedPinAttempts++;
            throw new IllegalArgumentException("Invalid PIN");
        }

        this.pin = pin;
        this.authenticated = true;
    }

    public double checkBalance() {
        ensureAuthenticated();
        return bank.checkBalance(cardNumber, cvv, pin);
    }

    public Map<Integer, Integer> withdraw(double amount) {
        ensureAuthenticated();

        // withdraw is a 2-step orchestration:
        // 1) reserve/deduct money from bank (in this demo: deduct)
        // 2) dispense cash from ATM
        bank.deductMoney(cardNumber, cvv, pin, amount);
        return atm.dispenseCash(amount);
    }

    public void close() {
        if (closedAt != null) return;
        closedAt = LocalDateTime.now();
        authenticated = false;
        pin = null;
    }

    private void ensureOpen() {
        if (closedAt != null) throw new IllegalStateException("Session already closed");
    }

    private void ensureAuthenticated() {
        ensureOpen();
        if (!authenticated) throw new IllegalStateException("Authenticate first");
    }
}
