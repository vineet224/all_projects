package practice.lld.firstiteration.coffeeold;

import java.util.EnumMap;
import java.util.Map;

public final class AddOns {
    private AddOns() {}

    public static Beverage milk(Beverage base) {
        return new SimpleAddOn(base, "Milk", 3000, req(Ingredient.MILK_ML, 50));
    }

    public static Beverage sugar(Beverage base) {
        return new SimpleAddOn(base, "Sugar", 500, req(Ingredient.SUGAR_G, 5));
    }

    // Using milk as a stand-in ingredient for demo (no dedicated cream/carame ingredient in inventory yet)
    public static Beverage whippedCream(Beverage base) {
        return new SimpleAddOn(base, "Whipped Cream", 4000, req(Ingredient.MILK_ML, 30));
    }

    public static Beverage caramel(Beverage base) {
        return new SimpleAddOn(base, "Caramel", 3500, req(Ingredient.SUGAR_G, 8));
    }

    private static final class SimpleAddOn extends AddOnDecorator {
        private final String name;
        private final int cost;
        private final Map<Ingredient, Integer> req;

        private SimpleAddOn(Beverage base, String name, int costInPaise, Map<Ingredient, Integer> required) {
            super(base);
            this.name = name;
            this.cost = costInPaise;
            this.req = required;
        }

        @Override
        protected String addOnName() {
            return name;
        }

        @Override
        protected int addOnCostInPaise() {
            return cost;
        }

        @Override
        protected Map<Ingredient, Integer> addOnIngredients() {
            return req;
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

