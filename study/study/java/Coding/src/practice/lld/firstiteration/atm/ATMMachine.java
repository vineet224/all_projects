package practice.lld.firstiteration.atm;


import java.util.HashMap;

/***
 *  functional requirements
 *  - user should able to login based on the card insertion // first do without session
 *  - user should checkBalance
 *  - user should make a request for cash of some amount
 *  - user should be able to to choose the notes on it's own (out of scope for now)
 *
 *  Non functional requirements
 *  - the amount debit from the users account should only be once and the cash should be given
 *  - if no cash gone out of the system them user account should deflect correct money
 *
 *  Entity
 *  - CurrencyNote
 *  - Receipt
 *  - User
 *
 */

//class CurrencyNote{
//    int val;
//    int totalAvailable;
//} using hashmap only in its place

class Receipt{
    int id;
    int userId;
    double amt;
    HashMap<Integer, Integer> notes;

    static int idCount =0;

    public Receipt(int userId, double amt, HashMap<Integer, Integer> notes){
        this.userId = userId;
        this.amt = amt;
        this.id = idCount;
        this.notes = notes;
        idCount++;
    }
}

class User{
    int id;
}

class Session{
    int id;
    int userId;
    int cardNumber;
    int hashPassword;

    static int idCount = 0;

    public Session(int userId, int cardNumber, int hashPassword){
        this.userId = userId;
        this.id = idCount;
        idCount++;
        this.cardNumber = cardNumber;
        this.hashPassword = hashPassword;
    }

    // we could have start time and the session time out things here
    // session has the card details and is already connected with the bank servers

}


public class ATMMachine {

    HashMap<Integer, Integer> totalAvailableNotes;
    HashMap<Integer, Receipt> allReceipt;

    Session currentSession;


    public ATMMachine(HashMap<Integer, Integer> totalAvailableNotes){
        this.totalAvailableNotes = totalAvailableNotes;
    }

    // login
    // checkBalance
    // withdrawAmt

    public boolean insertCardLogin(int cardNumber, int pin, int userId) throws Exception {

        // check if currentSession is already active them deny the operation

        if(currentSession != null){
            System.out.println(" session is already there");
            throw new Exception("session is already there ");
        }

        currentSession = new Session(userId, cardNumber, pin);

        // call the third party bank apis' to check if the cardNumber and pin are valid
        // currently assume those are returned true from the bank

        return true;
    }

    public double checkBalance() throws Exception {
        if(currentSession == null){
            System.out.println(" session timed out plead try again");
            throw new Exception("session timed out ");
        }

        System.out.println(" the request received to check the balance of user: "+ currentSession.userId);
        // using session cardinfo call the third party bank and get the info

        double bankResponseAmt = 1200.0;

        return bankResponseAmt;
    }

    public Receipt withAmount(double amount) throws Exception {
        if(currentSession == null){
            System.out.println("Session timeout please login again");
            throw new Exception("session timed out ");
        }

        double availableBalance = checkBalance();

        if(availableBalance < amount ){
            System.out.println(" current balance is less the then the amt");
            throw new Exception("current balance less the the requested ");
        }

        // call the third bank apis and deduct the money

        HashMap<Integer, Integer> notes = getDispensableNotes(amount);

        Receipt receipt = new Receipt(currentSession.userId, amount, notes);

        return receipt;
    }

    private HashMap<Integer, Integer> getDispensableNotes(double amt){

        HashMap<Integer, Integer> notes = new HashMap<>();

        for(Integer key: totalAvailableNotes.keySet()){
            int numberOfNotes = totalAvailableNotes.get(key);
            if(numberOfNotes >0 && key <= (int)amt){
                int maxNotesRequired = (int)amt/ key;
                int numberofNotesUsed = Math.min(numberOfNotes, maxNotesRequired);
                amt = amt - (numberofNotesUsed * key);
                notes.put(key, numberofNotesUsed);
                totalAvailableNotes.put(key, numberOfNotes - numberofNotesUsed); // is this simultaneous updation is allowed
            }
        }

        return notes;
    }


    public static void main(String[] args) throws Exception {

        HashMap<Integer,Integer> notes = new HashMap<>();
        notes.put(10,1000);
        notes.put(100,1000);
        notes.put(500,500);

        ATMMachine atmMachine = new ATMMachine(notes);

        atmMachine.insertCardLogin(123, 123, 1);

        double amt = atmMachine.checkBalance();

        System.out.println(" the balance of the user is ");

        Receipt receipt = atmMachine.withAmount(900.0);

        for(Integer key: receipt.notes.keySet()){
            System.out.println(" the val of note is "+ key + " the quantity of notes "+ receipt.notes.get(key));
        }

    }

}

/***
 * three major issues
 * - the bank deduction should happen after the available cash is checked
 * - the remaining amt is not 0 and not even mentioned any where
 * - rollback for dispense
 * - tree map as some notes are very much used so that is later strategy thing
 */

