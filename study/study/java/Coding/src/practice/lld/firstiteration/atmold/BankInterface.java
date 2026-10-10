package practice.lld.firstiteration.atmold;

public interface BankInterface {
    public double checkBalance(String cardNumber, String cvv, String pin);
    public double deductMoney(String cardNumber, String cvv, String pin, double amount);
    public boolean validatePin(String cardNumber, String cvv, String pin);

    public boolean isCardValid(String cardNumber, String cvv);
}
