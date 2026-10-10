package practice.lld.firstiteration.vendingmachine;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;

/***
 * funtional requirements :
 * - all items should be displayed
 * - user can choose a single base item
 * - user can select the addon for that item
 * - user can select another base item and repeat the process
 * - user finally checks out the selected items
 * - user pays for the bill
 * - dispatching of the items start
 *
 *
 * non funtional requiremnts
 * - latency of the dispense should be fast
 * - all the add ons should be correctly added
 *
 * class BaseItem
 * class AdoonItem
 * class Order
 * class Bill // we don't require this we will use the order only and store the require fields in the order only like the payment status instead of creating seperate Bill class
 *
 * Enum OrderDispenseStatus {pending,IN_progress, Completed, failed }
 * Enum OrderPaymentStatus {Checkout, paymentPending, paymentfailed , paymentCompleted}
 *
 *
 */

class AddonItem{ // this basically represent the items that are in our selves or in the vending machine
    int id;
    int totalQuantity;
    int price;
}

class BaseItem{ // this basically represent the items that are in our selves or in the vending machine
    int id;
    int totalQuantity;
    int price;
    ArrayList<AddonItem> allSupportedAddons;
}

class OrderedAddon{
    int id;
    int quantity;
}

class OrderItem{
    int id;
    int baseItemId;
    int quantity;
    ArrayList<OrderedAddon> addonItems; // i don't think we should use the class that represents the total amount stored here
    int orderItemPrice;

}

enum OrderPaymentStatus{
    CHECKOUT,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAYMENT_SUCCESS
}

enum OrderDispenseStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED
}

class Order{
    int id;
    ArrayList<OrderItem> orderItems;
    int totalPrice;
    OrderPaymentStatus paymentStatus;
    OrderDispenseStatus dispenseStatus;
}


public class VendingMachine {

    HashMap<Integer, BaseItem> allBaseItems;
    HashMap<Integer, AddonItem> allAddonItems;

    Queue<OrderItem> allActiveOrder;



    // get_all_item()
    // createOrder() return OrderId
    // add_baseItem(int orderId, OrderItem orderItem)
    // add_addon(int orderId, int orderItemid, OrderAddonItem addonItem)
    // checkout(int orderId)
    // makePayment(int Orderid) // this will call the internal function start dispensing
    // start_dispensing(int orderid)

}
