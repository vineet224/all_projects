package practice.lld.firstiteration.coffeeold;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

abstract class AbstractBeverage implements Beverage {

    private final String name;
    private final int costInPaise;
    private final EnumMap<Ingredient, Integer> required;

    protected AbstractBeverage(String name, int costInPaise, Map<Ingredient, Integer> requiredIngredients) {
        this.name = name;
        this.costInPaise = costInPaise;
        this.required = new EnumMap<>(Ingredient.class);
        if (requiredIngredients != null) this.required.putAll(requiredIngredients);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getCostInPaise() {
        return costInPaise;
    }

    @Override
    public Map<Ingredient, Integer> getRequiredIngredients() {
        return Collections.unmodifiableMap(required);
    }
}

