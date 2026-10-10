package practice.lld.firstiteration.coffeeold;

public class CashPayment implements PaymentMethod {
    @Override
    public PaymentReceipt pay(int amountInPaise) {
        if (amountInPaise <= 0) throw new IllegalArgumentException("Amount must be positive");
        return new PaymentReceipt(name(), amountInPaise);
    }

    @Override
    public void refund(PaymentReceipt receipt) {
        if (receipt == null) return;
        receipt.refunded = true;
    }

    @Override
    public String name() {
        return "CASH";
    }
}

