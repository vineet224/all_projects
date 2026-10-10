package practice.lld.seconditeration.atmmachine;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: ATM MACHINE & VAULT MANAGEMENT
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Authenticate user via Bank backend.
 * - Check balance & Withdraw cash.
 * - Dispense correct denominations (Greedy Algorithm).
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Transaction Atomicity: Bank balance MUST NOT deduct if ATM cannot dispense exact notes.
 * - Hardware Constraints: ATM must know if it physically has the right denomination mix.
 * * 3. ASSUMPTIONS:
 * - A physical ATM handles ONE user at a time locally. We don't need heavy thread-locking
 * on the ATM terminal itself, but the Bank Backend must handle concurrency.
 * - Denominations are limited to standard notes (e.g., ₹500, ₹100).
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Distributed Transactions: "Since the Bank and ATM are separate systems, this is a
 * classic 2-Phase Commit. We must pre-verify the ATM vault, then call the Bank, then
 * physically dispense."
 * - Hardware Failures: "If the Bank deducts the money, but the mechanical dispenser jams,
 * the ATM must write a 'FailureEvent' to a local WAL (Write-Ahead Log) and asynchronously
 * retry a refund request to the Bank via an Outbox pattern/Kafka."
 * - Caching: "I wouldn't cache the user's balance on the ATM. All balance reads must
 * hit the Bank DB synchronously to prevent overdrafts."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - The full 8-class State Design Pattern (HasCard, PinEntered, etc.) to prioritize the
 * vault logic and transaction atomicity.
 * - Multi-currency support and Admin Refill routing.
 * =======================================================================================
 */

// --- 1. Core Interfaces & Mock Bank ---

interface BankSystem {
    boolean authenticate(String cardId, String pin);
    double getBalance(String cardId);
    boolean deductBalance(String cardId, double amount);
    void refundBalance(String cardId, double amount);
}

class MockBankBackend implements BankSystem {
    private final Map<String, Double> accounts = new ConcurrentHashMap<>();
    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    public MockBankBackend() {
        accounts.put("CARD-123", 5000.00);
        credentials.put("CARD-123", "1234");
    }

    @Override
    public boolean authenticate(String cardId, String pin) {
        return pin.equals(credentials.get(cardId));
    }

    @Override
    public double getBalance(String cardId) {
        return accounts.getOrDefault(cardId, 0.0);
    }

    @Override
    public boolean deductBalance(String cardId, double amount) {
        // Concurrency check for backend
        synchronized (cardId.intern()) {
            double current = accounts.getOrDefault(cardId, 0.0);
            if (current >= amount) {
                accounts.put(cardId, current - amount);
                return true;
            }
            return false;
        }
    }

    @Override
    public void refundBalance(String cardId, double amount) {
        synchronized (cardId.intern()) {
            accounts.put(cardId, accounts.getOrDefault(cardId, 0.0) + amount);
        }
    }
}

// --- 2. Core Vault Engine (The Algorithm) ---

class CashVault {
    // TreeMap with reverse order so we always iterate largest denomination first (Greedy)
    private final TreeMap<Integer, Integer> inventory = new TreeMap<>(Collections.reverseOrder());

    public void addNotes(int denomination, int count) {
        inventory.put(denomination, inventory.getOrDefault(denomination, 0) + count);
    }

    public int getTotalCash() {
        return inventory.entrySet().stream().mapToInt(e -> e.getKey() * e.getValue()).sum();
    }

    /**
     * CORE SIGNAL: Calculate dispensing WITHOUT deducting yet.
     * We must ensure the ATM can fulfill the request before hitting the Bank.
     */
    public Map<Integer, Integer> calculateDispenseStrategy(int amount) {
        Map<Integer, Integer> notesToDispense = new HashMap<>();
        int remainingAmount = amount;

        for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
            int denom = entry.getKey();
            int availableNotes = entry.getValue();

            if (remainingAmount >= denom && availableNotes > 0) {
                int notesNeeded = remainingAmount / denom;
                int notesToTake = Math.min(notesNeeded, availableNotes);

                notesToDispense.put(denom, notesToTake);
                remainingAmount -= (notesToTake * denom);
            }
        }

        if (remainingAmount > 0) {
            return null; // Cannot fulfill exact amount with current denominations
        }
        return notesToDispense;
    }

    /**
     * Called strictly AFTER the Bank confirms the deduction.
     */
    public void commitDispense(Map<Integer, Integer> notesToDispense) {
        for (Map.Entry<Integer, Integer> entry : notesToDispense.entrySet()) {
            int denom = entry.getKey();
            int count = entry.getValue();
            inventory.put(denom, inventory.get(denom) - count);
        }
    }
}

// --- 3. ATM Orchestrator ---

class ATMMachine {
    private final BankSystem bank;
    private final CashVault vault;

    public ATMMachine(BankSystem bank, CashVault vault) {
        this.bank = bank;
        this.vault = vault;
    }

    public void withdraw(String cardId, String pin, int amount) {
        System.out.println("\n--- Initiating Withdrawal: ₹" + amount + " ---");

        // 1. Authentication
        if (!bank.authenticate(cardId, pin)) {
            System.out.println("❌ Auth Failed: Invalid PIN.");
            return;
        }

        // 2. Local Hardware Check: Can the ATM dispense this exact amount?
        Map<Integer, Integer> notesStrategy = vault.calculateDispenseStrategy(amount);
        if (notesStrategy == null) {
            System.out.println("❌ ATM Hardware Error: Cannot dispense exact denominations for ₹" + amount);
            return;
        }

        // 3. Bank Backend Check: Does the user have money?
        if (!bank.deductBalance(cardId, amount)) {
            System.out.println("❌ Bank Error: Insufficient Account Balance.");
            return;
        }

        // 4. Physical Dispense & Commit
        try {
            // SIMULATING HARDWARE DISPENSE (Could throw exception if mechanical jam occurs)
            vault.commitDispense(notesStrategy);
            System.out.println("✅ Dispense Successful! Notes:");
            notesStrategy.forEach((denom, count) -> System.out.println("   ₹" + denom + " x " + count));
            System.out.println("   Remaining Bank Balance: ₹" + bank.getBalance(cardId));

        } catch (Exception hardwareFailure) {
            // 5. Fatal Hardware Failure -> ROLLBACK required
            System.out.println("⚠️ CRITICAL: Hardware jammed! Refunding Bank Backend...");
            bank.refundBalance(cardId, amount);
        }
    }
}

// --- 4. Main Execution ---

public class Main {
    public static void main(String[] args) {
        BankSystem sbiBank = new MockBankBackend();
        CashVault atmVault = new CashVault();

        // Load ATM with ₹500 and ₹100 notes only
        atmVault.addNotes(500, 2);  // ₹1000 total in 500s
        atmVault.addNotes(100, 5);  // ₹500 total in 100s
        // Total ATM Cash = ₹1500

        ATMMachine atm = new ATMMachine(sbiBank, atmVault);

        // SCENARIO 1: Success (Greedy Algorithm kicks in)
        // User wants ₹1200. ATM should give 2x500 and 2x100.
        atm.withdraw("CARD-123", "1234", 1200);

        // SCENARIO 2: Bank Auth / Funds Check
        // User wants ₹6000, but only has ₹3800 left in Bank.
        atm.withdraw("CARD-123", "1234", 6000);

        // SCENARIO 3: Denomination Failure
        // User wants ₹400. ATM only has ₹500 and ₹100 left (Wait, ATM has 3x100 left).
        // Let's ask for ₹350. ATM cannot dispense 50s.
        atm.withdraw("CARD-123", "1234", 350);

        // SCENARIO 4: Insufficient ATM Vault Cash
        // User asks for ₹500. ATM only has ₹300 left (3x100 notes).
        atm.withdraw("CARD-123", "1234", 500);
    }
}