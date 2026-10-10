package practice.lld.firstiteration.atmold;

import java.util.HashMap;
import java.util.Map;

public class BankImplementation implements BankInterface{
    private static final class BankAccount {
        private final String cardNumber;
        private final String cvv;
        private final String pin;
        private double balance;

        private BankAccount(String cardNumber, String cvv, String pin, double balance) {
            this.cardNumber = cardNumber;
            this.cvv = cvv;
            this.pin = pin;
            this.balance = balance;
        }
    }

    private final Map<String, BankAccount> accountsByCardNumber = new HashMap<>();

    public BankImplementation() {
        // demo accounts
        accountsByCardNumber.put("1111-2222-3333-4444", new BankAccount("1111-2222-3333-4444", "123", "0000", 50_000));
        accountsByCardNumber.put("5555-6666-7777-8888", new BankAccount("5555-6666-7777-8888", "999", "1111", 10_000));
    }

    @Override
    public double checkBalance(String cardNumber, String cvv, String pin) {
        if (!validatePin(cardNumber, cvv, pin)) {
            throw new IllegalArgumentException("Invalid PIN");
        }
        BankAccount account = accountsByCardNumber.get(cardNumber);
        if (account == null) throw new IllegalArgumentException("Invalid card");
        synchronized (account) {
            return account.balance;
        }
    }

    @Override
    public double deductMoney(String cardNumber, String cvv, String pin, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (!validatePin(cardNumber, cvv, pin)) {
            throw new IllegalArgumentException("Invalid PIN");
        }

        BankAccount account = accountsByCardNumber.get(cardNumber);
        if (account == null) throw new IllegalArgumentException("Invalid card");

        synchronized (account) {
            if (account.balance < amount) {
                throw new IllegalStateException("Insufficient bank balance");
            }
            account.balance -= amount;
            return account.balance;
        }
    }

    @Override
    public boolean validatePin(String cardNumber, String cvv, String pin) {
        BankAccount account = accountsByCardNumber.get(cardNumber);
        if (account == null) return false;
        return account.cvv.equals(cvv) && account.pin.equals(pin);
    }

    @Override
    public boolean isCardValid(String cardNumber, String cvv) {
        BankAccount account = accountsByCardNumber.get(cardNumber);
        if (account == null) return false;
        return account.cvv.equals(cvv);
    }
}
