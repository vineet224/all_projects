package practice.lld.firstiteration.vendingmachinechatgpt;

import java.util.List;
import java.util.Map;

class Item {
    int id;
    String name;
    int price;
    int quantity;

    List<Integer> supportedAddons;
}

class Order {
    int id;

    Map<Integer,Integer> items;
    // itemId -> quantity

    int totalPrice;

    boolean paid;
}

class VendingChatgpt {

    Map<Integer, Item> inventory;

    Map<Integer, Order> orders;

    int orderIdCounter = 1;

    public int createOrder() {
        Order order = new Order();
        order.id = orderIdCounter++;

        orders.put(order.id, order);

        return order.id;
    }

    public void addItem(
            int orderId,
            int itemId,
            int qty
    ) {

        Order order = orders.get(orderId);

        order.items.put(
                itemId,
                order.items.getOrDefault(itemId,0)
                        + qty
        );

        order.totalPrice +=
                inventory.get(itemId).price * qty;
    }

    public void checkout(int orderId) {

        Order order = orders.get(orderId);

        System.out.println(
                "Total = "
                        + order.totalPrice
        );
    }

    public void pay(int orderId) {

        Order order = orders.get(orderId);

        order.paid = true;

        dispense(order);
    }

    private void dispense(Order order) {

        for(Integer itemId :
                order.items.keySet()) {

            int qty =
                    order.items.get(itemId);

            inventory.get(itemId)
                    .quantity -= qty;
        }

        System.out.println(
                "Dispensed order "
                        + order.id
        );
    }
}
