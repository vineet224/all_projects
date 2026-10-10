package practice.lld.thirditeration.splitwise.peerbalance;

import java.util.ArrayList;
import java.util.HashMap;

enum ExpenseType{EQUAL,EXACT}

class Split{
    String userId;
    double amount;
    public Split(String userId, double amount) {
        this.userId = userId;
        this.amount = amount;
    }
}

class User{
    String userId;
    double amount;
    public User(String userId) {
        this.userId = userId;
    }
}

public class SplitwiseApp {

    HashMap<String, HashMap<String,Double>> lender_borrower_map = new HashMap<>();
    HashMap<String, User> users = new HashMap<>();

    public void registerUser(String userId){
        User user = new User(userId);
        lender_borrower_map.putIfAbsent(userId, new HashMap<>());
    }

    public void addExpense(ArrayList<Split> splits, ExpenseType expenseType, String paidByUser, Double totalAmount){
        double avgAmount = totalAmount/splits.size();
        if(expenseType == ExpenseType.EQUAL){
            for(Split split: splits){
                split.amount = avgAmount;
            }
        }


    }

    private void processSplits(ArrayList<Split> splits, String lenderId){

        for(Split split: splits){
            if(lenderId.equals(split.userId)) continue;
            HashMap<String, Double> lender_borrowers = lender_borrower_map.get(lenderId);
            lender_borrowers.putIfAbsent(split.userId, 0.0);
            lender_borrowers.put(split.userId, lender_borrowers.get(split.userId) + split.amount);
        }
    }

    public void computeBalance(){


    }
}
