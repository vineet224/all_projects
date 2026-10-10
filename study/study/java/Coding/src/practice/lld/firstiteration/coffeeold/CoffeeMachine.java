package practice.lld.firstiteration.coffeeold;

public class CoffeeMachine {

    private final Inventory inventory = Inventory.getInstance();
    private CoffeeMachineState state = States.IDLE;

    public synchronized CoffeeMachineState getState() {
        return state;
    }

    /**
     * Basic happy-path orchestration:
     * - validate ingredients (and atomically consume)
     * - take payment
     * - dispense
     * - refund if dispense fails
     */
    public BrewResult buy(Beverage beverage, PaymentMethod paymentMethod) {
        if (beverage == null) throw new IllegalArgumentException("Beverage required");
        if (paymentMethod == null) throw new IllegalArgumentException("Payment method required");

        synchronized (this) {
            if (state == States.OUT_OF_ORDER) {
                return new BrewResult(beverage, null, false, "Machine out of order");
            }
            if (state != States.IDLE) {
                return new BrewResult(beverage, null, false, "Machine busy: " + state.name());
            }
            state = States.PROCESSING_PAYMENT;
        }

        PaymentReceipt receipt = null;
        try {
            // Concurrency requirement: reserve atomically, so 2 users can't consume the last milk simultaneously
            inventory.reserveAndConsume(beverage.getRequiredIngredients());

            receipt = paymentMethod.pay(beverage.getCostInPaise());

            synchronized (this) {
                state = States.DISPENSING;
            }

            // simulate brewing/dispensing
            simulateDispense();

            synchronized (this) {
                state = States.IDLE;
            }
            return new BrewResult(beverage, receipt, true,
                    "Dispensed " + beverage.getName() + " for " + Money.formatPaise(beverage.getCostInPaise()));
        } catch (Exception e) {
            if (receipt != null) {
                paymentMethod.refund(receipt);
            }
            synchronized (this) {
                state = States.OUT_OF_ORDER;
            }
            return new BrewResult(beverage, receipt, false, "Failed: " + e.getMessage());
        }
    }

    public void adminRefill(Ingredient ingredient, int quantity) {
        inventory.refill(ingredient, quantity);
        synchronized (this) {
            if (state == States.OUT_OF_ORDER) state = States.IDLE;
        }
    }

    private void simulateDispense() {
        // placeholder for boiler/pump/grinder operations
    }

    public static void main(String[] args) {
        CoffeeMachine machine = new CoffeeMachine();

        Beverage drink = AddOns.sugar(AddOns.milk(BaseDrinks.latte()));
        BrewResult r1 = machine.buy(drink, new CardPayment("4111111111111111"));
        System.out.println(r1.message + " | refunded=" + (r1.receipt != null && r1.receipt.refunded));

        Beverage drink2 = AddOns.whippedCream(AddOns.caramel(BaseDrinks.espresso()));
        BrewResult r2 = machine.buy(drink2, new CashPayment());
        System.out.println(r2.message + " | refunded=" + (r2.receipt != null && r2.receipt.refunded));
    }

}
