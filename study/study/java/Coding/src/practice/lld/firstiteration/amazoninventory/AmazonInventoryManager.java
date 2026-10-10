package practice.lld.firstiteration.amazoninventory;


import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/***
 * functional requirements
 * - user should be able to checkout an select a list of items
 * - user should search for avialbility of any item
 * - user should able to order for multiple items at one
 * - user should able to pay for the order
 *
 * non functional requirements:
 * - the inventory operation should be consistent
 * - i am not considering the user wallet here or user amount deductions here
 * -
 *
 *
 * entities:
 * Item
 * Inventory
 * order
 *
 */

class Product{
    String id;
    String name;
    double price;

    public Product(String id, String name, double price){
        this.id = id;
        this.price = price;
        this.name = name;
    }
}

class Inventory{
    public static ConcurrentHashMap<String, Integer> productStorage = new ConcurrentHashMap<>();

}

enum OrderStatus{
    CHECKOUT, IN_Progress, Success, Failed
}

class Order{
    String id;
    HashMap<String, Integer> items;
    double totalPrice;
    OrderStatus orderStatus;


    public Order(String id, HashMap<String, Integer> items, double price){
        this.id = id;
        this.items = items;
        this.totalPrice = price;
        // either we can calcluate the total price or we can just take it from the constructur
    }

}


public class AmazonInventoryManager {

    ConcurrentHashMap<String, Order> allOrder = new ConcurrentHashMap<>();
    ReentrantLock reentrantLock = new ReentrantLock();

    public int getAvaialableCount(String itemId){
        return Inventory.productStorage.get(itemId);
    }

    public void checkoutOrder(Order order){
        // check if all the items are avaialble

        if(allOrder.containsKey(order.id)){
            System.out.println(" this is duplicate request");
            return;
        }

        for(String itemId: order.items.keySet()){
            reentrantLock.lock(); // i should put the lock on the itemId level not the code level
            int itemCount = order.items.get(itemId);
            int availableCount = Inventory.productStorage.get(itemId);
            if(itemCount > availableCount){
                return ;
            }
            Inventory.productStorage.put(itemId, availableCount - itemCount);
            reentrantLock.unlock();
        }


        allOrder.put(order.id, order);
    }

    public void makePayment(String orderId){
        // this would do the transaction through the payment gateway
        Order order = allOrder.get(orderId);
        order.orderStatus = OrderStatus.Success;
        return;
    }

    public void cronJob(){

        for(Order order : allOrder.values()){
            if(order.orderStatus.equals(OrderStatus.Failed) || order.orderStatus.equals(OrderStatus.IN_Progress)){
                for(String itemId: order.items.keySet()){
                    reentrantLock.lock(); // i should put the lock on the itemId level not the code level
                    int itemCount = order.items.get(itemId);

                    Inventory.productStorage.compute(itemId, (k, availableCount) -> availableCount + itemCount);
                    reentrantLock.unlock();
                }
            }
        }
    }



}
