package practice.lld.thirditeration.fooddelivery.combined;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class FoodDeliveryLLD {

    enum PartnerStatus {
        AVAILABLE, BUSY
    }

    enum DeliveryStatus {
        PENDING,
        ASSIGNED,
        REACHED_RESTAURANT,
        PICKED_UP,
        DELIVERED,
        CANCELLED
    }

    static class Partner {
        final String id;
        PartnerStatus status = PartnerStatus.AVAILABLE;

        Partner(String id) {
            this.id = id;
        }
    }

    static class Delivery {
        final String orderId;
        volatile String partnerId;
        private DeliveryStatus status = DeliveryStatus.PENDING;

        Delivery(String orderId) {
            this.orderId = orderId;
        }

        synchronized DeliveryStatus getStatus() {
            return status;
        }

        synchronized void assign(String partnerId) {
            if (status != DeliveryStatus.PENDING)
                throw new IllegalStateException("Not pending");

            this.partnerId = partnerId;
            status = DeliveryStatus.ASSIGNED;
        }

        synchronized void transition(
                String actorId,
                DeliveryStatus expected,
                DeliveryStatus next) {

            if (!Objects.equals(partnerId, actorId))
                throw new SecurityException("Wrong partner");

            if (status != expected)
                throw new IllegalStateException(
                        "Invalid transition from " + status
                );

            status = next;
        }

        synchronized boolean cancelPending() {
            if (status != DeliveryStatus.PENDING)
                return false;

            status = DeliveryStatus.CANCELLED;
            return true;
        }
    }

    static class PartnerPool {
        private final Map<String, Partner> partners = new HashMap<>();
        private final Lock lock = new ReentrantLock();
        private final Condition available = lock.newCondition();

        void addPartner(String id) {
            lock.lock();
            try {
                if (partners.containsKey(id))
                    throw new IllegalArgumentException("Duplicate partner");

                partners.put(id, new Partner(id));
                available.signal();
            } finally {
                lock.unlock();
            }
        }

        String acquire() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (true) {
                    for (Partner partner : partners.values()) {
                        if (partner.status == PartnerStatus.AVAILABLE) {
                            partner.status = PartnerStatus.BUSY;
                            return partner.id;
                        }
                    }
                    available.await();
                }
            } finally {
                lock.unlock();
            }
        }

        void release(String partnerId) {
            lock.lock();
            try {
                Partner partner = partners.get(partnerId);

                if (partner == null ||
                        partner.status != PartnerStatus.BUSY)
                    throw new IllegalStateException("Invalid release");

                partner.status = PartnerStatus.AVAILABLE;
                available.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    static class DeliveryService {
        private final Map<String, Delivery> deliveries =
                new ConcurrentHashMap<>();

        private final BlockingQueue<Delivery> pending =
                new LinkedBlockingQueue<>();

        private final PartnerPool partnerPool;
        private final ExecutorService worker =
                Executors.newSingleThreadExecutor();

        private volatile boolean running = true;

        DeliveryService(PartnerPool partnerPool) {
            this.partnerPool = partnerPool;
        }

        void start() {
            worker.submit(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        Delivery delivery = pending.take();

                        if (delivery.getStatus() != DeliveryStatus.PENDING)
                            continue;

                        String partnerId = partnerPool.acquire();

                        synchronized (delivery) {
                            if (delivery.getStatus() ==
                                    DeliveryStatus.PENDING) {
                                delivery.assign(partnerId);
                                System.out.println(
                                        delivery.orderId + " -> " + partnerId
                                );
                            } else {
                                // Cancelled while waiting for a partner.
                                partnerPool.release(partnerId);
                            }
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        Delivery requestDelivery(String orderId) {
            if (!running)
                throw new IllegalStateException("Stopped");

            Delivery delivery = new Delivery(orderId);

            if (deliveries.putIfAbsent(orderId, delivery) != null)
                throw new IllegalArgumentException("Duplicate order");

            pending.offer(delivery);
            return delivery;
        }

        Delivery get(String orderId) {
            Delivery delivery = deliveries.get(orderId);
            if (delivery == null)
                throw new IllegalArgumentException("Unknown order");
            return delivery;
        }

        void reachedRestaurant(String orderId, String partnerId) {
            get(orderId).transition(
                    partnerId,
                    DeliveryStatus.ASSIGNED,
                    DeliveryStatus.REACHED_RESTAURANT
            );
        }

        void pickup(String orderId, String partnerId) {
            get(orderId).transition(
                    partnerId,
                    DeliveryStatus.REACHED_RESTAURANT,
                    DeliveryStatus.PICKED_UP
            );
        }

        void complete(String orderId, String partnerId) {
            Delivery delivery = get(orderId);

            synchronized (delivery) {
                delivery.transition(
                        partnerId,
                        DeliveryStatus.PICKED_UP,
                        DeliveryStatus.DELIVERED
                );
                partnerPool.release(delivery.partnerId);
            }
        }

        void cancel(String orderId, String partnerId) {
            Delivery delivery = get(orderId);

            synchronized (delivery) {
                DeliveryStatus current = delivery.getStatus();

                if (current == DeliveryStatus.PENDING) {
                    delivery.cancelPending();
                    return;
                }

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

                partnerPool.release(delivery.partnerId);
            }
        }

        void shutdown() {
            running = false;
            worker.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        PartnerPool pool = new PartnerPool();
        pool.addPartner("P1");

        DeliveryService service = new DeliveryService(pool);
        service.start();

        service.requestDelivery("O1");
        service.requestDelivery("O2");

        Thread.sleep(200);

        System.out.println("O1: " + service.get("O1").getStatus());
        System.out.println("O2: " + service.get("O2").getStatus());

        // Execute first delivery.
        service.reachedRestaurant("O1", "P1");
        service.pickup("O1", "P1");
        service.complete("O1", "P1");

        // Partner becomes available; worker assigns O2.
        Thread.sleep(200);

        System.out.println("O2: " + service.get("O2").getStatus());

        service.shutdown();
    }
}
