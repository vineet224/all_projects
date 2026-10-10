package practice.lld.firstiteration.coffeeold;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Inventory {
    private static final Inventory INSTANCE = new Inventory();

    private final EnumMap<Ingredient, Integer> available = new EnumMap<>(Ingredient.class);

    private Inventory() {
        // demo initial stock
        available.put(Ingredient.WATER_ML, 10_000);
        available.put(Ingredient.MILK_ML, 5_000);
        available.put(Ingredient.COFFEE_BEANS_G, 1_000);
        available.put(Ingredient.SUGAR_G, 500);
    }

    public static Inventory getInstance() {
        return INSTANCE;
    }

    public synchronized void refill(Ingredient ingredient, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Refill quantity must be positive");
        available.put(ingredient, available.getOrDefault(ingredient, 0) + quantity);
    }

    public synchronized Map<Ingredient, Integer> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(available));
    }

    public synchronized boolean canFulfill(Map<Ingredient, Integer> required) {
        for (var e : required.entrySet()) {
            int have = available.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) return false;
        }
        return true;
    }

    /**
     * Atomically checks and decrements inventory.
     * This is the key method for concurrency safety.
     */
    public synchronized void reserveAndConsume(Map<Ingredient, Integer> required) {
        if (!canFulfill(required)) {
            throw new IllegalStateException("Insufficient ingredients");
        }
        for (var e : required.entrySet()) {
            Ingredient ing = e.getKey();
            int remaining = available.getOrDefault(ing, 0) - e.getValue();
            available.put(ing, remaining);
        }
    }
}

