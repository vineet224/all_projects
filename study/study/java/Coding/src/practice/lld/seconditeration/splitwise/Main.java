package practice.lld.seconditeration.splitwise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: SPLITWISE (EXPENSE SHARING)
 * =======================================================================================
 *
 * The Time Sinks (What to cut)
 * Complex Graph Simplification (Min Cash Flow): Unless specifically asked, do not attempt to write the greedy heap-based debt simplification algorithm. It will eat your entire 45 minutes. Stick to a basic 2D ledger (A owes B).
 *
 * Groups & User Authentication: Cut them. Assume all users exist globally.
 *
 * Floating Point Precision: In production, you must use BigDecimal. In an interviews.interview, explain why you would use BigDecimal, but explicitly state you are using double with Math.round to save typing time.
 *
 *
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Add users to the system.
 * - Add an expense of type EQUAL or EXACT.
 * - Show balances for a single user or all users.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: Concurrent expense additions must not corrupt the ledger.
 * - Precision: Prevent floating point math errors (e.g., 33.333333...).
 * * 3. ASSUMPTIONS:
 * - Single currency (INR).
 * - Global group (no specific sub-groups to manage).
 * - "Paid By" is a single user for simplicity (extensible to multiple later).
 * * 4. HLD CALLOUTS (Drop these verbally):
 * - Float Precision: "I'm using doubles here to save interviews.interview time, but in production,
 * financial systems MUST use `BigDecimal` to prevent precision loss."
 * - Async Simplification: "If asked for debt simplification (Min Cash Flow algorithm),
 * I wouldn't run it synchronously on every expense. I'd trigger an async Kafka event
 * to a graph-processing worker, or run a nightly cron job."
 * - Event Sourcing: "To maintain an immutable audit trail of who edited what expense,
 * I would use Event Sourcing for the ledger rather than just updating balances in-place."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - PERCENT split type (logic is identical to EXACT, just requires a math step).
 * - Complex graph debt simplification (A owes B, B owes C -> A owes C).
 * =======================================================================================
 */

// --- 1. Enums & Models ---

enum ExpenseType { EQUAL, EXACT }

class Split {
    final String userId;
    double amount;

    public Split(String userId, double amount) {
        this.userId = userId;
        this.amount = amount;
    }
}

// --- 2. Strategy Pattern for Split Logic ---

interface SplitStrategy {
    List<Split> calculateSplits(double totalAmount, List<Split> providedSplits);
}

class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculateSplits(double totalAmount, List<Split> providedSplits) {
        int totalPeople = providedSplits.size();
        double splitAmount = Math.round((totalAmount / totalPeople) * 100.0) / 100.0;

        // Handle 1 cent/paisa fraction mismatch for the first person
        providedSplits.get(0).amount = splitAmount + (totalAmount - (splitAmount * totalPeople));
        for (int i = 1; i < totalPeople; i++) {
            providedSplits.get(i).amount = splitAmount;
        }
        return providedSplits;
    }
}

class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculateSplits(double totalAmount, List<Split> providedSplits) {
        double sum = 0;
        for (Split split : providedSplits) sum += split.amount;
        if (Math.abs(sum - totalAmount) > 0.01) {
            throw new IllegalArgumentException("Exact splits do not add up to total amount!");
        }
        return providedSplits; // Amounts are already provided correctly
    }
}

// --- 3. Core System (Ledger & Manager) ---

class ExpenseManager {
    // balanceSheet represents: Map<UserWhoOwes, Map<UserWhoGetsPaid, Amount>>
    private final Map<String, Map<String, Double>> balanceSheet = new ConcurrentHashMap<>();
    private final Map<ExpenseType, SplitStrategy> splitStrategies = new EnumMap<>(ExpenseType.class);

    public ExpenseManager() {
        splitStrategies.put(ExpenseType.EQUAL, new EqualSplitStrategy());
        splitStrategies.put(ExpenseType.EXACT, new ExactSplitStrategy());
    }

    public void addUser(String userId) {
        balanceSheet.putIfAbsent(userId, new ConcurrentHashMap<>());
    }

    /**
     * CORE SIGNAL: Updating the 2D Ledger Thread-Safely
     */
    public void addExpense(ExpenseType type, double amount, String paidBy, List<Split> splits) {
        SplitStrategy strategy = splitStrategies.get(type);
        List<Split> calculatedSplits = strategy.calculateSplits(amount, splits);

        // We synchronize on the paidBy user's ledger to prevent concurrent modification
        // issues if multiple threads add expenses involving this user simultaneously.
        synchronized (paidBy.intern()) {
            for (Split split : calculatedSplits) {
                String paidTo = split.userId;
                double oweAmount = split.amount;

                if (paidBy.equals(paidTo)) continue; // Ignore self-payment

                Map<String, Double> balancesOfPayer = balanceSheet.get(paidBy);
                Map<String, Double> balancesOfPayee = balanceSheet.get(paidTo);

                // If PaidBy already owes PaidTo, reduce that debt first
                double currentOweToPayee = balancesOfPayer.getOrDefault(paidTo, 0.0);
                if (currentOweToPayee >= oweAmount) {
                    balancesOfPayer.put(paidTo, currentOweToPayee - oweAmount);
                } else {
                    balancesOfPayer.put(paidTo, 0.0);
                    // Add the remaining debt to PaidTo's ledger (PaidTo owes PaidBy)
                    double newDebt = oweAmount - currentOweToPayee;
                    balancesOfPayee.put(paidBy, balancesOfPayee.getOrDefault(paidBy, 0.0) + newDebt);
                }
            }
        }
    }

    public void showBalances() {
        boolean isEmpty = true;
        for (Map.Entry<String, Map<String, Double>> allBalances : balanceSheet.entrySet()) {
            String borrower = allBalances.getKey();
            for (Map.Entry<String, Double> userBalance : allBalances.getValue().entrySet()) {
                String lender = userBalance.getKey();
                double amount = userBalance.getValue();
                if (amount > 0) {
                    System.out.println(borrower + " owes " + lender + ": ₹" + String.format("%.2f", amount));
                    isEmpty = false;
                }
            }
        }
        if (isEmpty) System.out.println("No balances.");
    }
}

// --- 4. Main Execution ---

public class Main {
    public static void main(String[] args) {
        System.out.println("=== INITIALIZING SPLITWISE ===\n");
        ExpenseManager app = new ExpenseManager();
        app.addUser("U1");
        app.addUser("U2");
        app.addUser("U3");
        app.addUser("U4");

        app.showBalances();

        System.out.println("\n=== SCENARIO 1: EXACT SPLIT ===");
        // U1 pays ₹1250 for U2 (₹370) and U3 (₹880)
        List<Split> exactSplits = Arrays.asList(new Split("U2", 370), new Split("U3", 880));
        app.addExpense(ExpenseType.EXACT, 1250, "U1", exactSplits);
        app.showBalances();

        System.out.println("\n=== SCENARIO 2: EQUAL SPLIT (With Fraction Handling) ===");
        // U4 pays ₹1000 for U1, U2, U3, U4
        List<Split> equalSplits = Arrays.asList(
                new Split("U1", 0), new Split("U2", 0), new Split("U3", 0), new Split("U4", 0)
        );
        app.addExpense(ExpenseType.EQUAL, 1000, "U4", equalSplits);
        app.showBalances();

        System.out.println("\n=== SCENARIO 3: DEBT OFFSETTING ===");
        // U2 pays ₹500 for U1. Since U2 currently owes U1, this should reduce U2's debt automatically.
        List<Split> offsetSplits = Arrays.asList(new Split("U1", 500));
        app.addExpense(ExpenseType.EXACT, 500, "U2", offsetSplits);
        app.showBalances();
    }
}
