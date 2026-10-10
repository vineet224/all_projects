package practice.lld.firstiteration.coffeeold;

import java.util.EnumMap;
import java.util.Map;

public final class BaseDrinks {
    private BaseDrinks() {}

    public static Beverage espresso() {
        // Simple recipe assumptions for demo
        return new Base("Espresso", 15000, req(
                Ingredient.WATER_ML, 30,
                Ingredient.COFFEE_BEANS_G, 8
        ));
    }

    public static Beverage cappuccino() {
        return new Base("Cappuccino", 22000, req(
                Ingredient.WATER_ML, 30,
                Ingredient.MILK_ML, 120,
                Ingredient.COFFEE_BEANS_G, 8
        ));
    }

    public static Beverage latte() {
        return new Base("Latte", 24000, req(
                Ingredient.WATER_ML, 30,
                Ingredient.MILK_ML, 180,
                Ingredient.COFFEE_BEANS_G, 8
        ));
    }

    private static final class Base extends AbstractBeverage {
        private Base(String name, int costInPaise, Map<Ingredient, Integer> requiredIngredients) {
            super(name, costInPaise, requiredIngredients);
        }
    }

    private static EnumMap<Ingredient, Integer> req(Object... pairs) {
        EnumMap<Ingredient, Integer> map = new EnumMap<>(Ingredient.class);
        for (int i = 0; i < pairs.length; i += 2) {
            Ingredient ing = (Ingredient) pairs[i];
            Integer qty = (Integer) pairs[i + 1];
            map.put(ing, qty);
        }
        return map;
    }
}

