package practice.lld.thirditeration.vendingmachine;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * functional requirements
 * - the system serve api to create a item/drink that could include the base drink and multiple addons
 * - In a single go user should able to create multiple items before checkout
 * - payment is out of scope
 *
 * nonfunctional requirements
 * - does we have to support multiple vending machine or specifically thread saftey
 * -
 * Assumption discussed with interviewer
 * - We will take int as the quantity for all even milk , hotwater or any other item
 * - Only Items that are of type BASE can be selected as base and any Item can be selected as Addon don't have to implement these checks confirmed with interviwers
 *
 */

enum ItemType{BASE,ADDON}
enum OrderStatus{Checkedout,Success,Failed}

class ItemInventory{
    String inventoryId;
    String itemId;
    int avaialbleQuantity;
    int holdingQuantity;
    ItemType  itemType;
    ArrayList<ItemInventory> allowedAddons;
    ReentrantLock lock = new ReentrantLock();
    String holderOrderId;
    public ItemInventory(String inventoryId,String itemId, int avaialbleQuantity,  ArrayList<ItemInventory> allowedAddons,ItemType itemType) {
        this.inventoryId = inventoryId;
        this.itemId = itemId;
        this.avaialbleQuantity = avaialbleQuantity;
        this.allowedAddons = allowedAddons;
        this.holdingQuantity = 0;
        this.itemType = itemType;
    }

    public void reserveQuantity(int quantity){
        this.avaialbleQuantity =  this.avaialbleQuantity - quantity;
        this.holdingQuantity = this.holdingQuantity + quantity; // how do we do both these transaction atomic not using db it is simple real life where we have DB I can always state that to interviewer
    }

    public int getItemQuantity(){
        return this.avaialbleQuantity;
    }

    public void restoreQuantity(int quantity){
        this.avaialbleQuantity =  this.avaialbleQuantity + quantity;
        this.holdingQuantity = this.holdingQuantity - quantity;
    }
}

class OrderItem{
    String  itemId;
    int quantity;
    ArrayList<OrderItem> addOnsForThisItem;
    public OrderItem(String itemId, int quantity,  ArrayList<OrderItem> addOnsForThisItem) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.addOnsForThisItem = addOnsForThisItem;
    }
}

class Order{
    String orderId;
    ArrayList<OrderItem> orderItems;
    OrderStatus orderStatus;
    public Order(String orderId, ArrayList<OrderItem> orderItems) {
        this.orderId = orderId;
        this.orderItems = orderItems;
        this.orderStatus = OrderStatus.Checkedout;
    }
}

public class VendingMachine {
    ConcurrentHashMap<String,ItemInventory> inventory =  new ConcurrentHashMap<>();
    ConcurrentHashMap<String,Order> orders = new ConcurrentHashMap<>();

    public void createOrder(Order order){
        if(orders.putIfAbsent(order.orderId, order)!=null){
            System.out.println("Order already exists");
            throw new IllegalArgumentException("order already present");
        }
        ArrayList<String> inventoryLockAccuired = new ArrayList<>();

        try{
            for(OrderItem orderItem : order.orderItems){
                ItemInventory itemInventory = inventory.get(orderItem.itemId);
                itemInventory.lock.lock();
                itemInventory.holderOrderId = order.orderId;
                inventoryLockAccuired.add(orderItem.itemId);
                for(OrderItem addOns : orderItem.addOnsForThisItem){
                    ItemInventory itemInventory2 = inventory.get(addOns.itemId);
                    itemInventory2.lock.lock();
                    itemInventory2.holderOrderId = order.orderId;
                    inventoryLockAccuired.add(addOns.itemId);
                }
            }
            // here update the amount
        }catch(Exception e){
            System.out.println("Error in inventory lock");
        }finally {
            for(String itemInventoryId : inventoryLockAccuired){
                ItemInventory itemInventory = inventory.get(itemInventoryId);
                if(Objects.equals(itemInventory.holderOrderId, order.orderId)){
                    itemInventory.lock.unlock();
                }
            }
        }


    }
}
