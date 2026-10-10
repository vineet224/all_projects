package practice.lld.thirditeration.fooddelivery.deliveryexecution;


import java.util.*;
import java.util.concurrent.*;

public class DeliveryExecution {

    enum DeliveryStatus {
        ASSIGNED,
        REACHED_RESTAURANT,
        PICKED_UP,
        DELIVERED,
        CANCELLED
    }

    static class Delivery {
        final String id;
        final String orderId;
        final String partnerId;

        private DeliveryStatus status = DeliveryStatus.ASSIGNED;

        Delivery(String id, String orderId, String partnerId) {
            this.id = id;
            this.orderId = orderId;
            this.partnerId = partnerId;
        }

        public synchronized DeliveryStatus getStatus() {
            return status;
        }

        public synchronized void transition(
                String actorId,
                DeliveryStatus expected,
                DeliveryStatus next) {

            if (!partnerId.equals(actorId))
                throw new SecurityException("Wrong delivery partner");

            if (status != expected)
                throw new IllegalStateException(
                        "Expected " + expected + ", actual " + status
                );

            status = next;
        }
    }

    static class PartnerService {
        private final Set<String> busyPartners =
                ConcurrentHashMap.newKeySet();

        public void reserve(String partnerId) {
            if (!busyPartners.add(partnerId))
                throw new IllegalStateException("Partner already busy");
        }

        public void release(String partnerId) {
            if (!busyPartners.remove(partnerId))
                throw new IllegalStateException("Partner not busy");
        }
    }

    static class DeliveryService {
        private final Map<String, Delivery> deliveries =
                new ConcurrentHashMap<>();

        private final PartnerService partnerService;

        DeliveryService(PartnerService partnerService) {
            this.partnerService = partnerService;
        }

        public Delivery createDelivery(
                String orderId, String partnerId) {

            partnerService.reserve(partnerId);

            String deliveryId = UUID.randomUUID().toString();
            Delivery delivery =
                    new Delivery(deliveryId, orderId, partnerId);

            deliveries.put(deliveryId, delivery);
            return delivery;
        }

        private Delivery get(String id) {
            Delivery delivery = deliveries.get(id);
            if (delivery == null)
                throw new IllegalArgumentException("Delivery not found");
            return delivery;
        }

        public void reachedRestaurant(String id, String partnerId) {
            get(id).transition(
                    partnerId,
                    DeliveryStatus.ASSIGNED,
                    DeliveryStatus.REACHED_RESTAURANT
            );
        }

        public void pickup(String id, String partnerId) {
            get(id).transition(
                    partnerId,
                    DeliveryStatus.REACHED_RESTAURANT,
                    DeliveryStatus.PICKED_UP
            );
        }

        public void complete(String id, String partnerId) {
            Delivery delivery = get(id);

            synchronized (delivery) {
                delivery.transition(
                        partnerId,
                        DeliveryStatus.PICKED_UP,
                        DeliveryStatus.DELIVERED
                );
                partnerService.release(delivery.partnerId);
            }
        }

        public void cancel(String id, String partnerId) {
            Delivery delivery = get(id);

            synchronized (delivery) {
                DeliveryStatus current = delivery.getStatus();

                if (current != DeliveryStatus.ASSIGNED &&
                        current != DeliveryStatus.REACHED_RESTAURANT)
                    throw new IllegalStateException(
                            "Cannot cancel after pickup"
                    );

                delivery.transition(
                        partnerId,
                        current,
                        DeliveryStatus.CANCELLED
                );

                partnerService.release(delivery.partnerId);
            }
        }

        public DeliveryStatus getStatus(String id) {
            return get(id).getStatus();
        }
    }

    public static void main(String[] args) {
        PartnerService partners = new PartnerService();
        DeliveryService service = new DeliveryService(partners);

        Delivery delivery = service.createDelivery("O1", "P1");

        service.reachedRestaurant(delivery.id, "P1");
        service.pickup(delivery.id, "P1");
        service.complete(delivery.id, "P1");

        System.out.println(service.getStatus(delivery.id));

        // P1 is free again and can handle another delivery.
        Delivery second = service.createDelivery("O2", "P1");
        System.out.println(second.getStatus());
    }
}
