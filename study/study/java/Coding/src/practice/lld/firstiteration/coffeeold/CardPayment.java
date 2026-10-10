package practice.lld.firstiteration.coffeeold;

public class CardPayment implements PaymentMethod {
    private final String maskedCard;

    public CardPayment(String cardNumber) {
        this.maskedCard = mask(cardNumber);
    }

    @Override
    public PaymentReceipt pay(int amountInPaise) {
        if (amountInPaise <= 0) throw new IllegalArgumentException("Amount must be positive");
        // In real world: talk to payment gateway.
        return new PaymentReceipt(name() + "(" + maskedCard + ")", amountInPaise);
    }

    @Override
    public void refund(PaymentReceipt receipt) {
        if (receipt == null) return;
        receipt.refunded = true;
    }

    @Override
    public String name() {
        return "CARD";
    }

    private static String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        return "****-****-****-" + last4;
    }
}

