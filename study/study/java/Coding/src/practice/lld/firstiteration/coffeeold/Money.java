package practice.lld.firstiteration.coffeeold;

public final class Money {
    private Money() {}

    public static String formatPaise(int paise) {
        int rupees = paise / 100;
        int rem = Math.abs(paise % 100);
        return "₹" + rupees + "." + (rem < 10 ? "0" + rem : rem);
    }
}

