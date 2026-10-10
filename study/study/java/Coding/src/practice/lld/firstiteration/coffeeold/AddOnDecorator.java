package practice.lld.firstiteration.coffeeold;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public abstract class AddOnDecorator implements Beverage {
    protected final Beverage base;

    protected AddOnDecorator(Beverage base) {
        this.base = Objects.requireNonNull(base);
    }

    protected abstract String addOnName();
    protected abstract int addOnCostInPaise();
    protected abstract Map<Ingredient, Integer> addOnIngredients();

    @Override
    public String getName() {
        return base.getName() + " + " + addOnName();
    }

    @Override
    public int getCostInPaise() {
        return base.getCostInPaise() + addOnCostInPaise();
    }

    @Override
    public Map<Ingredient, Integer> getRequiredIngredients() {
        EnumMap<Ingredient, Integer> merged = new EnumMap<>(Ingredient.class);
        base.getRequiredIngredients().forEach((k, v) -> merged.merge(k, v, Integer::sum));
        addOnIngredients().forEach((k, v) -> merged.merge(k, v, Integer::sum));
        return Collections.unmodifiableMap(merged);
    }
}

