package practice.lld.firstiteration.splitwisechatgpt;

import java.util.*;

class User {
    int id;
    String name;
    double balance; // +ve => should receive, -ve => should pay

    static int idCounter = 1;

    public User(String name) {
        this.id = idCounter++;
        this.name = name;
        this.balance = 0;
    }
}

class Participant {
    int userId;

    double paidAmount;
    double shareAmount;

    public Participant(
            int userId,
            double paidAmount,
            double shareAmount
    ) {
        this.userId = userId;
        this.paidAmount = paidAmount;
        this.shareAmount = shareAmount;
    }
}

class Expense {
    int id;

    List<Participant> participants;

    static int idCounter = 1;

    public Expense(
            List<Participant> participants
    ) {
        this.id = idCounter++;
        this.participants = participants;
    }
}

class Settlement {
    int fromUserId;
    int toUserId;
    double amount;

    public Settlement(
            int fromUserId,
            int toUserId,
            double amount
    ) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "User "
                + fromUserId
                + " pays User "
                + toUserId
                + " -> "
                + amount;
    }
}

class Splitwise {

    Map<Integer, User> users =
            new HashMap<>();

    Map<Integer, Expense> expenses =
            new HashMap<>();


    public User addUser(String name) {

        User user =
                new User(name);

        users.put(user.id, user);

        return user;
    }


    public void addExpense(
            Expense expense
    ) {

        expenses.put(
                expense.id,
                expense
        );

        for (Participant p :
                expense.participants) {

            User user =
                    users.get(
                            p.userId
                    );

            user.balance +=
                    (p.paidAmount
                            - p.shareAmount);
        }
    }


    public List<Settlement>
    getSettlements() {

        List<User> receivers =
                new ArrayList<>();

        List<User> payers =
                new ArrayList<>();


        for (User user :
                users.values()) {

            if (user.balance > 0.0001) {

                receivers.add(user);

            } else if (
                    user.balance < -0.0001) {

                payers.add(user);
            }
        }


        receivers.sort(
                (a, b) ->
                        Double.compare(
                                b.balance,
                                a.balance
                        )
        );

        payers.sort(
                (a, b) ->
                        Double.compare(
                                a.balance,
                                b.balance
                        )
        );

        List<Settlement> ans =
                new ArrayList<>();


        int i = 0;
        int j = 0;

        while (
                i < receivers.size()
                        && j < payers.size()
        ) {

            User receiver =
                    receivers.get(i);

            User payer =
                    payers.get(j);

            double amount =
                    Math.min(
                            receiver.balance,
                            -payer.balance
                    );

            ans.add(
                    new Settlement(
                            payer.id,
                            receiver.id,
                            amount
                    )
            );

            receiver.balance -= amount;
            payer.balance += amount;

            if (Math.abs(
                    receiver.balance
            ) < 0.0001) {
                i++;
            }

            if (Math.abs(
                    payer.balance
            ) < 0.0001) {
                j++;
            }
        }

        return ans;
    }
}


public class SplitwiseChatgpt {

    public static void main(
            String[] args
    ) {

        Splitwise splitwise =
                new Splitwise();

        User A =
                splitwise.addUser("A");

        User B =
                splitwise.addUser("B");

        User C =
                splitwise.addUser("C");


        List<Participant> expense1 =
                new ArrayList<>();

        expense1.add(
                new Participant(
                        A.id,
                        900,
                        300
                )
        );

        expense1.add(
                new Participant(
                        B.id,
                        0,
                        300
                )
        );

        expense1.add(
                new Participant(
                        C.id,
                        0,
                        300
                )
        );

        splitwise.addExpense(
                new Expense(expense1)
        );


        System.out.println(
                "Balances:"
        );

        for (User user :
                splitwise.users.values()) {

            System.out.println(
                    user.name
                            + " -> "
                            + user.balance
            );
        }

        System.out.println(
                "\nSettlements:"
        );

        List<Settlement>
                settlements =
                splitwise.getSettlements();

        for (Settlement s :
                settlements) {

            System.out.println(s);
        }
    }
}