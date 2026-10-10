package practice.lld.firstiteration.coffeeold;

public interface PaymentMethod {
    PaymentReceipt pay(int amountInPaise);
    void refund(PaymentReceipt receipt);
    String name();
}

