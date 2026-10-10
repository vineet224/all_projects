package practice.lld.firstiteration.coffeeold;

public final class BrewResult {
    public final Beverage beverage;
    public final PaymentReceipt receipt;
    public final boolean success;
    public final String message;

    public BrewResult(Beverage beverage, PaymentReceipt receipt, boolean success, String message) {
        this.beverage = beverage;
        this.receipt = receipt;
        this.success = success;
        this.message = message;
    }
}

