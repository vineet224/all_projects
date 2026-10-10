package practice.lld.thirditeration.splitwise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * lld functional rqeuirement
 * - the user can add the expense including the participants and there contribution
 * - the user should able to do get who all he owned or who all owes him
 * - the user should be able to make settlement for single partner who the user owes and it should update the user owes or owed balance
 * - assumption in the settlement user can be mapped to a user he hasn't been involved in a direct transaciton like (a owes b) and seperately (b owes c) then it is possible for settlement a needs to pay c or a is mapped with c
 *
 */
class User{

    String userId;
    double owedbalance; // this is negative if user owes some one and it would be positive if he is to recieve money from other
    HashMap<String,Double> expensesHistory;

    public User(String userId){this.owedbalance=0.0;this.userId=userId;this.expensesHistory=new HashMap<>();}

    public synchronized void update(double balance, String expenseId){
        owedbalance+=balance;
        expensesHistory.put(expenseId,balance);
    }
}

class Expense{
    String expenseIdempotentId;
    double totalamount;
    ConcurrentHashMap<String, Double> payers;
    ArrayList<String> allInvlovedUsers;
    public Expense(String expenseIdempotentId,double totalamount, ConcurrentHashMap<String, Double> payers, ArrayList<String> allInvlovedUsers) {
        this.totalamount=totalamount;
        this.payers=payers;
        this.allInvlovedUsers = allInvlovedUsers;
        this.expenseIdempotentId =  expenseIdempotentId;
    }
}

record UserAmountPair(String userId, double amount){}

interface SettlementStrategy{

    public HashMap<String,ArrayList<UserAmountPair>>  createTotalSettlement(HashMap<String,User> users);
}

class HighestDebitToHighestCreditMatchStrategy implements SettlementStrategy{

    @Override
    public HashMap<String,ArrayList<UserAmountPair>> createTotalSettlement(HashMap<String,User> users) {
        HashMap<String,ArrayList<UserAmountPair>> owersPerUser = new HashMap<>();
        ArrayList<UserAmountPair> userWhoOwes = new ArrayList<>();
        ArrayList<UserAmountPair> userWhoAreOwed =  new ArrayList<>();

        for(String userId : users.keySet()){
            if(users.get(userId).owedbalance>0){
                userWhoAreOwed.add(new UserAmountPair(userId,users.get(userId).owedbalance));
            }else if(users.get(userId).owedbalance<0){
                userWhoOwes.add(new UserAmountPair(userId,users.get(userId).owedbalance));
            }
        }

        Collections.sort(userWhoAreOwed, (a,b) -> {return Double.compare(b.amount(),a.amount());});
        Collections.sort(userWhoOwes, (a,b) -> {return Double.compare(a.amount(),b.amount());});

        for(int i=0;i<userWhoAreOwed.size();i++){
            owersPerUser.put(userWhoAreOwed.get(i).userId(),new ArrayList<>());
        }

        int j =0;
        for(int i=0;i<userWhoAreOwed.size();i++){
            Double amountToRecieve = userWhoAreOwed.get(i).amount();
            while(amountToRecieve>0 && j<userWhoOwes.size()){
                if((-1*userWhoOwes.get(j).amount())>amountToRecieve){
                    ArrayList<UserAmountPair> owees =  owersPerUser.get(userWhoAreOwed.get(i).userId());
                    owees.add(new UserAmountPair(userWhoOwes.get(i).userId(),amountToRecieve));
                    userWhoOwes.set(j,new UserAmountPair(userWhoOwes.get(j).userId(),userWhoOwes.get(j).amount() +  amountToRecieve));
                    amountToRecieve = 0.0;
                }else {
                    ArrayList<UserAmountPair> owees = owersPerUser.get(userWhoAreOwed.get(i).userId());
                    owees.add(new UserAmountPair(userWhoOwes.get(i).userId(),userWhoOwes.get(j).amount()));
                    amountToRecieve = amountToRecieve +  userWhoOwes.get(j).amount();
                    j++;
                }

                if(amountToRecieve==0){
                    i++;
                }
            }
        }
        return owersPerUser;
    }
}

public class SplitwiseApp {

    HashMap<String,User> users = new HashMap<>();
    SettlementStrategy  settlementStrategy = new HighestDebitToHighestCreditMatchStrategy();

    public void addExpense(Expense expense){
        // assuming that users are not repeated in the allInvolvedUsers
        // the sum of the payers would sum upto the totalamount

        try{
            int totalInvolvedUsers = expense.allInvlovedUsers.size();
            double avgAmount = expense.totalamount/totalInvolvedUsers;
            for(String userId: expense.payers.keySet()){
                User  user = users.get(userId);
                double userPaidInThisExpense = expense.payers.get(userId);
                double extraBalance = avgAmount - userPaidInThisExpense;
                user.update(extraBalance,expense.expenseIdempotentId);
            }
        }catch(Exception e){
            System.out.printf("Error occur while processing expense");
            reconcileThisExpense(expense);
        }
    }

    private void reconcileThisExpense(Expense expense){
        try{
            int totalInvolvedUsers = expense.allInvlovedUsers.size();
            double avgAmount = expense.totalamount/totalInvolvedUsers;
            for(String userId: expense.payers.keySet()){
                User  user = users.get(userId);
                if(user.expensesHistory.containsKey(expense.expenseIdempotentId)){
                    System.out.println(" this user already get updated by this expense skip this");
                    continue;
                }
                double userPaidInThisExpense = expense.payers.get(userId);
                double extraBalance = avgAmount - userPaidInThisExpense;
                user.update(extraBalance,expense.expenseIdempotentId);
            }
        }catch(Exception e){
            System.out.printf("Error occur while reconciling expense");
            reconcileThisExpense(expense);
        }
    }

    public HashMap<String,ArrayList<UserAmountPair>> getSettlement(){
        HashMap<String,ArrayList<UserAmountPair>> owersPerUser = new HashMap<>();
        owersPerUser = settlementStrategy.createTotalSettlement(users);
        return owersPerUser;
    }
}
