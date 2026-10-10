package practice.lld.firstiteration.splitwise;

import java.util.ArrayList;
import java.util.HashMap;

/***
 *  funcitonal:
 *     - user should be able to add expense and the persons including in it
 *     - User should see the option to pay to clear his debt
 *
 *   non functional:
 *   - use should have to pay minimum number of transaction to clear his debt
 *   - user should recive money in min transactions as well
 *
 *   entity:
 *   - expense {amount, paid_by:{} , users{}}
 *   - user { name, upiId, total_to_receive, total_to_pay}
 *
 *
 *
 *
 */

class User{
    int id;
    String name;
    static int idCount = 0;
    double total_to_pay;
    double total_to_receive; // assuming that these two are not greater then 0 simulatneously

    public User(String name){
        id = idCount;
        idCount++;
        this.name = name;
    }

    public void updateUserPaidExtra(double amt){
        // first reduce this amt from the total_to_pay for users
        if(total_to_receive >0){
            total_to_receive = total_to_receive + amt;
        }else{
            total_to_pay = total_to_pay - amt;
            if(total_to_pay <0){
                total_to_receive += (-1 * total_to_pay);
                total_to_pay = 0;
            }
        }
    }

    public void updateUserPaidLess(double amt){

        if(total_to_pay > 0){
            total_to_pay += amt;
        }else{
            total_to_receive = total_to_receive - amt;
            if(total_to_receive <0 ){
                total_to_pay += (-1 * total_to_receive);
                total_to_receive = 0;
            }
        }
    }

}

class Participant{
    int userId;
    double amountPaid;
    public Participant(int userId, double amountPaid){
        this.userId = userId;
        this.amountPaid = amountPaid;
    }
}

class Expense{
    int id;
    String name;
    ArrayList<Participant> participants;
    static int idCount = 0;

    public Expense(String name, ArrayList<Participant> participants){
        this.name = name;
        this.participants = participants;
        this.id = idCount;
        idCount++;
    }
}


public class Splitwise {

    HashMap<Integer, User> users;
    HashMap<Integer, Expense> allExpenses;

    // addExpense(userId, expense)

    public Expense addExpense(int userId, Expense expense){
        // if userId is valid
        // validate the expense
        updateUserDataForExpense(expense);
        allExpenses.put(expense.id, expense);
        return expense;
    }

    private void updateUserDataForExpense(Expense expense){
        int totalAmount = 0;
        double amtPerPerson = 0;
        ArrayList<Participant> allParticipants = expense.participants;
        for(int i=0;i<allParticipants.size();i++){
            totalAmount += allParticipants.get(i).amountPaid;
        }

        amtPerPerson = (double) totalAmount/(double) allParticipants.size();

        for(int i=0;i<allParticipants.size();i++){
            double amtPaidByi = allParticipants.get(i).amountPaid;
            User user = users.get(allParticipants.get(i).userId);
            if(amtPaidByi == amtPerPerson){
                continue;
            }
            else if( amtPaidByi > amtPerPerson){
                user.updateUserPaidExtra(amtPaidByi - amtPerPerson);
            }else{
                user.updateUserPaidLess(amtPerPerson - amtPaidByi);
            }
        }
    }



    public ArrayList<Participant> settleMent(int userId){
        // now here we just match the users that has to pay and that has to recieve money
        ArrayList<User> allToRecieve;
        ArrayList<User> allToPay;

//        sortbytotalTOpay(allToPay)
//        sortbytotalToREceive(allToRecieve);


        return null;
    }


}
