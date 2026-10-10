package practice.lld.firstiteration.paymentgateway;

import java.util.ArrayList;
import java.util.HashMap;

/***
 * - function requirements
 * - as payment gateway our client(swiggy,ecommerce) should user our multiple api's to get the payment from the their clients(end user)
 * - so our payment gateway should support multiple bank-integrations
 * - and for each bank multiple payments ways like (debit card, upi, netbanking)
 *
 * non functional requirements
 * - payment should be consistent no double call to the bank servers and success should be send when the payment is done
 * - we should keep status like payment in_progress, failed, success
 * - there should be a way for reconciliation
 *
 * entities:
 * - "Payment Gateways service"
 * - - get_ways_to_pay() response is the ways that are supported by our payment gateway like upi, debit card, netbanking
 * - - select_a_payment_way() response is the fields that are required for this payment way
 * - - make_payment(Object details) response with the pending for now if the payment is in progress or not
 *
 * - "Payment" {id, amount, transactionId, bankDetails, paymentStatus}
 *
 *
 * -- issues that i could identify in the system is we haven't add anything realted to pending payments
 * - nothing about concurrency or how payment is done
 * - is the correct direction i should be focusing on in the payment gateway or what
 */

enum PaymentStatus{
    In_progress, Failed, Success
}

enum PaymentWay{
    Credit,
    UPI,
    NetBanking
}

interface PaymentCredentials{

}

class CreditPaymentCredentials implements PaymentCredentials {
    String cardNumber;
    String cvv;
    String expiry;
}

class UpiPaymentCredentials implements PaymentCredentials{
    String upiId;
    public UpiPaymentCredentials(String upiId){this.upiId=upiId;}
}

class NetBankingPaymentCredentials implements PaymentCredentials{
    String userName;
    String password;
}


class Payment{
    int id;
    int transactionId;
    int userId;
    PaymentStatus status;
    PaymentWay paymentWay;


    public Payment(int id, int transactionId, int userId , PaymentWay paymentWay){
        this.id = id;
        this.transactionId = transactionId;
        this.userId = userId;
         this.paymentWay = paymentWay;
         this.status = PaymentStatus.In_progress;
    }

}

interface PaymentMethod{
    boolean makePayment(Payment payment, PaymentCredentials paymentCredentials);
}

class UPIPaymentMethod implements PaymentMethod{

    @Override
    public boolean makePayment(Payment payment, PaymentCredentials paymentCredentials) {
        // check paymentdetails
        System.out.println(" the Payment is done through UPI");
        return true;
    }
}

class CreditPaymentMethod implements PaymentMethod{

    @Override
    public boolean makePayment(Payment payment, PaymentCredentials paymentCredentials) {
        System.out.println(" the Payment is done through credit");
        return true;
    }
}

class NetBankingPayMethod implements PaymentMethod{

    @Override
    public boolean makePayment(Payment payment, PaymentCredentials paymentCredentials) {
        System.out.println(" the Payment is done through netbaking");
        return true;
    }
}



public class PaymentGateway {

    ArrayList<PaymentWay> supportedWays;
    ArrayList<Payment> allPayments;

    HashMap<PaymentWay, PaymentMethod> paymentWayPaymentMethodHahsMap;


    public PaymentGateway(){
        this.supportedWays = new ArrayList<>();
        supportedWays.add(PaymentWay.Credit);
        supportedWays.add(PaymentWay.UPI);
        supportedWays.add(PaymentWay.NetBanking);
        allPayments = new ArrayList<>();
        paymentWayPaymentMethodHahsMap = new HashMap<>();
        paymentWayPaymentMethodHahsMap.put(PaymentWay.Credit, new CreditPaymentMethod());
        paymentWayPaymentMethodHahsMap.put(PaymentWay.NetBanking, new NetBankingPayMethod());
        paymentWayPaymentMethodHahsMap.put(PaymentWay.UPI, new UPIPaymentMethod());
    }

    public ArrayList<PaymentWay> getSupportedWays(){
        return supportedWays;
    }

    public Payment makePaymentThrough(Payment payment, PaymentCredentials paymentCredentials){
        // check the paymentcredentials of the correct type

        PaymentMethod currentPaymentMethod = paymentWayPaymentMethodHahsMap.get(payment.paymentWay);
        boolean is_success = currentPaymentMethod.makePayment(payment, paymentCredentials);

        if(is_success == true){
            payment.status = PaymentStatus.Success;
        }else{
            payment.status = PaymentStatus.Failed;
        }

        allPayments.add(payment);

        return payment;
    }


    public ArrayList<Payment> getAllPaymentForUser(int userId ){

        ArrayList<Payment> response = new ArrayList<>();

        for(Payment payment: allPayments){
            if(payment.userId == userId){
                response.add(payment);
            }
        }

        return response;
    }


    public static void main(String[] args){
        PaymentGateway paymentGateway = new PaymentGateway();

        Payment payment = new Payment(1,1,1,PaymentWay.UPI);
        PaymentCredentials paymentCredentials = new UpiPaymentCredentials("upiid1");

        Payment response = paymentGateway.makePaymentThrough(payment, paymentCredentials);

        System.out.println(" the staus of the payment "+ response.status);
    }
}
