package practice.lld.seconditeration.coffeevendingmachine;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: COFFEE VENDING MACHINE
 * =======================================================================================
 *
 * The Time Sinks (What to cut)
 * The State Pattern: Keep it out unless explicitly asked. Focus on the brewing engine.
 *
 * Payment/Change Calculation: Mock it. The ATM problem already proves you can do math.
 *
 * Complex UI layers: Skip.
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Define recipes for different drinks (Espresso, Cappuccino).
 * - Check inventory, dispense drink, and deduct ingredients automatically.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: Concurrent orders MUST NOT cause negative inventory.
 * - Deadlock Prevention: Multi-ingredient deduction must be lock-ordered.
 * - Extensibility: Adding a new drink (e.g., Latte) should not alter core brewing logic.
 * * 3. ASSUMPTIONS:
 * - Focus is on the Brewing Engine (Backend). Payment processing is mocked/external.
 * - Inventory is held in-memory for the single physical machine.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Distributed Inventory: "If this was a cloud-connected fleet of machines, I'd use
 * Kafka to stream inventory deduction events to a central DB for analytics and
 * automated refill dispatching."
 * - LUA Scripts: "In a real distributed environment, I'd replace these Java ReentrantLocks
 * with a Redis LUA script to atomically check and deduct all ingredients in one network hop."
 * - Alerting: "I'd implement an Observer Pattern or an async event publisher to trigger
 * a 'LowStockAlert' when any ingredient drops below a threshold."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Hardware State Machine (Idle -> Payment -> Brewing), Admin Refill APIs,
 * and exact temperature/brewing time simulations.
 * =======================================================================================
 */

// --- 1. Enums & Models ---

enum Ingredient { WATER, MILK, COFFEE_BEANS, SUGAR }

class Recipe {
    final String name;
    final Map<Ingredient, Integer> requiredIngredients;

    public Recipe(String name, Map<Ingredient, Integer> requirements) {
        this.name = name;
        this.requiredIngredients = requirements;
    }
}

// --- 2. Core Inventory Engine ---

class InventoryManager {
    private final Map<Ingredient, Integer> stock = new ConcurrentHashMap<>();
    private final Map<Ingredient, ReentrantLock> locks = new ConcurrentHashMap<>();

    public InventoryManager() {
        for (Ingredient i : Ingredient.values()) {
            stock.put(i, 0);
            locks.put(i, new ReentrantLock());
        }
    }

    public void refill(Ingredient i, int amount) {
        locks.get(i).lock();
        try {
            stock.put(i, stock.get(i) + amount);
        } finally {
            locks.get(i).unlock();
        }
    }

    public void displayStock() {
        System.out.println("--- Current Stock ---");
        stock.forEach((k, v) -> System.out.println(k + ": " + v + " units"));
    }

    /**
     * CORE SIGNAL: Atomic multi-resource deduction with Deadlock Prevention.
     */
    public boolean consumeForRecipe(Recipe recipe) {
        // 1. DEADLOCK PREVENTION: Always acquire locks in a consistent order (Enum ordinal)
        List<Ingredient> required = new ArrayList<>(recipe.requiredIngredients.keySet());
        Collections.sort(required);

        List<Ingredient> lockedIngredients = new ArrayList<>();
        boolean canBrew = true;

        try {
            // 2. ACQUIRE LOCKS
            for (Ingredient i : required) {
                // Wait max 2 seconds to avoid frozen threads
                if (locks.get(i).tryLock(2, TimeUnit.SECONDS)) {
                    lockedIngredients.add(i);
                } else {
                    canBrew = false;
                    break;
                }
            }

            // 3. CHECK AVAILABILITY
            if (canBrew) {
                for (Ingredient i : required) {
                    if (stock.get(i) < recipe.requiredIngredients.get(i)) {
                        System.out.println("❌ Not enough " + i + " for " + recipe.name);
                        canBrew = false;
                        break;
                    }
                }
            }

            // 4. ATOMIC DEDUCTION
            if (canBrew) {
                for (Ingredient i : required) {
                    stock.put(i, stock.get(i) - recipe.requiredIngredients.get(i));
                }
                return true;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            canBrew = false;
        } finally {
            // 5. CRITICAL: Always unlock to prevent memory leaks/freezes
            for (Ingredient i : lockedIngredients) {
                locks.get(i).unlock();
            }
        }
        return false;
    }
}

// --- 3. Machine Orchestrator ---

class CoffeeMachine {
    private final InventoryManager inventory;
    private final Map<String, Recipe> menu = new HashMap<>();

    public CoffeeMachine(InventoryManager inventory) {
        this.inventory = inventory;

        // Extensibility: Recipes are configured, not hardcoded into methods
        menu.put("ESPRESSO", new Recipe("Espresso", Map.of(
                Ingredient.WATER, 50,
                Ingredient.COFFEE_BEANS, 15
        )));

        menu.put("CAPPUCCINO", new Recipe("Cappuccino", Map.of(
                Ingredient.WATER, 50,
                Ingredient.MILK, 100,
                Ingredient.COFFEE_BEANS, 15,
                Ingredient.SUGAR, 10
        )));
    }

    public void selectDrink(String drinkName) {
        Recipe recipe = menu.get(drinkName.toUpperCase());
        if (recipe == null) {
            System.out.println("❌ Unknown drink: " + drinkName);
            return;
        }

        System.out.println("⏳ Brewing " + drinkName + "...");
        if (inventory.consumeForRecipe(recipe)) {
            System.out.println("✅ " + drinkName + " is ready! Enjoy.");
        } else {
            System.out.println("❌ Brewing failed. Please call maintenance.");
        }
    }
}

// --- 4. Main Execution & Concurrency Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        InventoryManager inventory = new InventoryManager();

        // Refill exactly enough for 2 Cappuccinos (200 Milk, 30 Beans, 100 Water, 20 Sugar)
        inventory.refill(Ingredient.WATER, 100);
        inventory.refill(Ingredient.MILK, 200);
        inventory.refill(Ingredient.COFFEE_BEANS, 30);
        inventory.refill(Ingredient.SUGAR, 20);

        CoffeeMachine machine = new CoffeeMachine(inventory);
        inventory.displayStock();

        System.out.println("\n=== CONCURRENCY TEST: FLASH MOB ORDER ===");
        // 3 people hit the Cappuccino button at the exact same millisecond.
        // We only have ingredients for 2. The 3rd MUST fail gracefully.
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Runnable orderDrink = () -> machine.selectDrink("CAPPUCCINO");

        executor.submit(orderDrink);
        executor.submit(orderDrink);
        executor.submit(orderDrink);

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        System.out.println("\n=== FINAL STOCK ===");
        inventory.displayStock(); // All should be perfectly 0
    }
}