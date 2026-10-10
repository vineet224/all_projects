package practice.lld.firstiteration.coffeeold;

import java.time.LocalDateTime;
import java.util.UUID;

public final class PaymentReceipt {
    public final String receiptId;
    public final String method;
    public final int amountInPaise;
    public final LocalDateTime paidAt;
    public boolean refunded;

    public PaymentReceipt(String method, int amountInPaise) {
        this.receiptId = "R-" + UUID.randomUUID();
        this.method = method;
        this.amountInPaise = amountInPaise;
        this.paidAt = LocalDateTime.now();
        this.refunded = false;
    }
}

