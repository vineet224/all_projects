package practice.lld.firstiteration.coffeeold;

import java.util.Map;

public interface Beverage {
    String getName();
    int getCostInPaise();
    Map<Ingredient, Integer> getRequiredIngredients();
}

