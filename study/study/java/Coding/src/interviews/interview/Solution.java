package interviews.interview;

import java.util.*;


//Core Functional Expectations:
//Restaurant Discovery:
//Users should be able to search for restaurants by Name. (should easily support other filters also).
//Only nearby restaurants (based on the user's current location) should be returned in search results.
//
//
//        Ordering Flow:
//        Users can explore a restaurant’s menu and choose food items to prepare for an order.
//        The system should support the ability to place an order with a selected restaurant.
//        Note: An order must be associated with a single restaurant.
//
//
//        Capacity Constraints:
//        Each restaurant has a limit on how many orders it can process concurrently.
//        If the number of active orders exceeds this threshold, the restaurant should not accept new orders until some of the current orders are marked as completed.


// Futntional requirement
// - user should able to serach by name restraunt  return list  - it should not return the restrautns that are over the capacity ,, here we have to get restraunt based on distance/
// - user should able to get all the list of food item in the restrouant menu when he selects restraunt
// - user can select multiple food items from a restraunt and order
// - update the constraints of an restraunt like to free the capacity or fill up


// core entities
// User {userId, name, location}
// restraunt {name, restrautnId, location, List<MenuItem>, Capacity , Queue<currentOrders>}
// menu Item {itemId, name , price}
// Order {List<MenuItem>, orderId, userId, restrautnId, enum status, totalamount}
// Location -- will see later

import java.util.List;

class Location{
    int lat;
    int log;
}

enum OrderStatus{
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED
}

class User {
    int userId;
    String name;
    Location location;

    static int userIdCount = 0; // it is not thread safe currently
    public User (String name){
        this.name = name;
        this.userId = userIdCount;
        userIdCount++;
        location = null;
    }
}

class MenuItem{
    int itemId;
    String name;
    double price;

    static int itemIdCount = 0;

    public MenuItem(String name, double price){
        this.name = name;
        this.price = price;
        this.itemId = itemIdCount;
        itemIdCount++;
    }
}

class Order{
    int orderId;
    int userId;
    int resId;
    double totalAmount;
    OrderStatus orderStatus;
    List<MenuItem> menuItems;
    static int orderIdCount = 0; // not thread safe
    public Order(int userId, int resId, double totalAmount, List<MenuItem> items){
        this. orderId = orderIdCount;
        orderIdCount++;
        this.userId = userId;
        this.resId = resId;
        this.totalAmount = totalAmount;
        this.menuItems = items;
    }
}


class Restraunt{
    int resId;
    String name;
    Location location;
    List<MenuItem> menuItems;
    int capacity;
    Queue<Order> ordersInExecution; // it will not store the past completed orders

    static int resIdCount = 0;

    public Restraunt(String name, List<MenuItem> menuItem, int capacity ){
        this.name = name;
        this.resId = resIdCount;
        resIdCount++;
        this.menuItems = menuItem;
        this.capacity = capacity;
        ordersInExecution = new LinkedList<>();
    }

    public Order addNewOrder(Order order){
        ordersInExecution.add(order);
        return order;
    }

    public boolean canPlaceAnOrder(){
        if(ordersInExecution.size() >= capacity) return false;
        return true;
    }

    public int process(){
        Order order = ordersInExecution.poll();
        order.orderStatus = OrderStatus.COMPLETED;
        return order.orderId;
    }

}

class FoodManagerServie{
    HashMap<String, Restraunt> restrauntHashMap = new HashMap<>();

    HashMap<Integer, User> userHashMap = new HashMap<>();
    // map of restrautn and orders
    HashMap<Integer, Order> currentActiveOrders = new HashMap<>(); // to track all the orders placed

    // register user
    public User addUser(User user){
        userHashMap.put(user.userId, user);
        return user;
    }

    // register a restraunt
    public Restraunt addRestraunt(Restraunt restraunt){
        restrauntHashMap.put(restraunt.name, restraunt);
        return restraunt;
    }

    // get list of restraunt by name;
    //public ArrayList<Restraunt> getRestraunts(RestrauntQueryFilter restrauntQuery) {}

    public ArrayList<Restraunt> getRestrauntByName(String name){
        ArrayList<Restraunt> restraunts = new ArrayList<>();

        Restraunt restrauntWithName = restrauntHashMap.getOrDefault(name, null);
        if(restrauntWithName == null) {
            System.out.println(" no restrautn with this name");
        }else{
            restraunts.add(restrauntWithName);
        }

        return restraunts;
    }



    public Order placeAnOrder(List<MenuItem> menuItems, String name, int userId){
        // we will validate if all the menu belongs to the restraunt with id , validate if user exist , validate if restraunt exist

        // call the check capacity function seperatly

        Restraunt restraunt = restrauntHashMap.get(name);
        if(restraunt == null){
            System.out.println(" we can't get the restraunt");
            return null;
        }
        if(!restraunt.canPlaceAnOrder()){
            return null;
        }

        double totalAmount = 0.0;
        for(int i=0;i<menuItems.size();i++){
            totalAmount = totalAmount + menuItems.get(i).price;
        }
        Order newOrder = new Order(userId, restraunt.resId,totalAmount, menuItems);

        restraunt.addNewOrder(newOrder);

        currentActiveOrders.put(newOrder.orderId, newOrder);

        return newOrder;
    }



}


public class Solution {
    public static void main(String[] args){
        System.out.println(" hello world");
        FoodManagerServie foodManagerServie = new FoodManagerServie();

        MenuItem item1 = new MenuItem("pizza", 200.0);
        MenuItem item2 = new MenuItem("Burger", 100.0);

        List<MenuItem> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);

        User user = new User("vineet");

        Restraunt restraunt = new Restraunt("Resa", items, 2);
        foodManagerServie.addRestraunt(restraunt);
        foodManagerServie.addUser(user);

        ArrayList<Restraunt> restraunts = foodManagerServie.getRestrauntByName("Resa");

        for(int i=0;i<restraunts.size();i++){
            System.out.println(" "+ restraunts.get(i).name);
        }

        Restraunt restraunt1 = restraunts.get(0);

        Order order = foodManagerServie.placeAnOrder(items, restraunt1.name, user.userId);

        System.out.println(" order price "+ order.totalAmount);

        Order order2 = foodManagerServie.placeAnOrder(items, restraunt1.name, user.userId);

        System.out.println(" order price "+ order2.totalAmount);

        Order order3 = foodManagerServie.placeAnOrder(items, restraunt1.name, user.userId);

        System.out.println(" order price "+ order3.totalAmount); // give null

    }
}
