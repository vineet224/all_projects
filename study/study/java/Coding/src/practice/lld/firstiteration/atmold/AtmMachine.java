package practice.lld.firstiteration.atmold;

import java.util.HashMap;
import java.util.Map;

public class AtmMachine {

    private double cashInATM = 0.0;
    private final HashMap<Integer, Integer> currencyMap = new HashMap<>(); // denomination -> count
    private final BankInterface bank;

    public AtmMachine(BankInterface bank){
        this.bank = bank;

        // demo cash load (INR-like denominations)
        loadCash(2000, 100);
        loadCash(500, 200);
        loadCash(200, 200);
        loadCash(100, 500);
    }

    public synchronized void loadCash(int denomination, int count){
        if (denomination <= 0 || count <= 0) throw new IllegalArgumentException("Invalid cash load");
        currencyMap.put(denomination, currencyMap.getOrDefault(denomination, 0) + count);
        cashInATM += (double) denomination * count;
    }

    public synchronized double getCashInATM() {
        return cashInATM;
    }

    public synchronized Map<Integer, Integer> getCurrencySnapshot() {
        return Map.copyOf(currencyMap);
    }

    public Session insertCard(String cardNumber, String cvv){
        if (!bank.isCardValid(cardNumber, cvv)) {
            throw new IllegalArgumentException("Invalid card");
        }
        return new Session(this, bank, cardNumber, cvv);
    }

    synchronized Map<Integer, Integer> dispenseCash(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (amount != Math.floor(amount)) throw new IllegalArgumentException("ATM dispenses integer amounts only");

        if (cashInATM < amount) {
            throw new IllegalStateException("ATM has insufficient cash");
        }

        int target = (int) amount;
        HashMap<Integer, Integer> plan = CashDispenser.planDispense(currencyMap, target);
        if (plan.isEmpty()) {
            throw new IllegalStateException("Cannot dispense requested amount with available denominations");
        }

        for (var e : plan.entrySet()) {
            int denom = e.getKey();
            int used = e.getValue();
            currencyMap.put(denom, currencyMap.get(denom) - used);
        }
        cashInATM -= amount;
        return plan;
    }

    static final class CashDispenser {
        private CashDispenser() {}

        static HashMap<Integer, Integer> planDispense(Map<Integer, Integer> availableNotes, int amount) {
            // Greedy works for canonical denominations like INR (100,200,500,2000).
            // For arbitrary denominations, replace with DP/backtracking.
            int remaining = amount;
            HashMap<Integer, Integer> plan = new HashMap<>();

            var denoms = new java.util.ArrayList<>(availableNotes.keySet());
            denoms.sort((a, b) -> Integer.compare(b, a));
            for (int denom : denoms) {
                if (remaining <= 0) break;
                int notes = availableNotes.getOrDefault(denom, 0);
                if (notes <= 0) continue;
                int maxNeeded = remaining / denom;
                int used = Math.min(notes, maxNeeded);
                if (used > 0) {
                    plan.put(denom, used);
                    remaining -= denom * used;
                }
            }

            if (remaining != 0) return new HashMap<>();
            return plan;
        }
    }

    public static void main(String[] args) {
        BankInterface bank = new BankImplementation();
        AtmMachine atm = new AtmMachine(bank);

        Session session = atm.insertCard("1111-2222-3333-4444", "123");
        session.authenticate("0000");
        System.out.println("Balance: " + session.checkBalance());
        System.out.println("Withdraw plan: " + session.withdraw(3700));
        System.out.println("Balance after withdraw: " + session.checkBalance());
        session.close();
    }
}
