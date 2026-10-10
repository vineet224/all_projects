package practice.lld.thirditeration.fooddelivery.deliveryassignment;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class DeliveryAssignment {

    enum PartnerStatus {
        AVAILABLE, BUSY
    }

    enum OrderStatus {
        PENDING, ASSIGNED
    }

    static class DeliveryPartner {
        final String id;
        PartnerStatus status = PartnerStatus.AVAILABLE;

        DeliveryPartner(String id) {
            this.id = id;
        }
    }

    static class Order {
        final String id;
        volatile OrderStatus status = OrderStatus.PENDING;
        volatile String partnerId;

        Order(String id) {
            this.id = id;
        }
    }

    static class PartnerService {
        private final Map<String, DeliveryPartner> partners =
                new HashMap<>();

        private final Lock lock = new ReentrantLock();
        private final Condition available = lock.newCondition();

        public void addPartner(String id) {
            lock.lock();
            try {
                if (partners.containsKey(id))
                    throw new IllegalArgumentException("Duplicate partner");

                partners.put(id, new DeliveryPartner(id));
                available.signal();
            } finally {
                lock.unlock();
            }
        }

        // Blocks until a partner becomes available.
        public DeliveryPartner acquirePartner()
                throws InterruptedException {

            lock.lockInterruptibly();
            try {
                while (true) {
                    for (DeliveryPartner partner : partners.values()) {
                        if (partner.status == PartnerStatus.AVAILABLE) {
                            partner.status = PartnerStatus.BUSY;
                            return partner;
                        }
                    }
                    available.await();
                }
            } finally {
                lock.unlock();
            }
        }

        public void releasePartner(String id) {
            lock.lock();
            try {
                DeliveryPartner partner = partners.get(id);

                if (partner == null)
                    throw new IllegalArgumentException("Unknown partner");

                if (partner.status != PartnerStatus.BUSY)
                    throw new IllegalStateException("Partner not busy");

                partner.status = PartnerStatus.AVAILABLE;
                available.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    static class AssignmentService {
        private final BlockingQueue<Order> pending =
                new LinkedBlockingQueue<>();

        private final Map<String, Order> orders =
                new ConcurrentHashMap<>();

        private final PartnerService partnerService;
        private final ExecutorService worker =
                Executors.newSingleThreadExecutor();

        private volatile boolean running = true;

        AssignmentService(PartnerService partnerService) {
            this.partnerService = partnerService;
        }

        public void start() {
            worker.submit(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        Order order = pending.take();

                        DeliveryPartner partner =
                                partnerService.acquirePartner();

                        order.partnerId = partner.id;
                        order.status = OrderStatus.ASSIGNED;

                        System.out.println(
                                order.id + " assigned to " + partner.id
                        );

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        public void requestAssignment(String orderId) {
            if (!running)
                throw new IllegalStateException("Service stopped");

            Order order = new Order(orderId);

            if (orders.putIfAbsent(orderId, order) != null)
                throw new IllegalArgumentException("Duplicate order");

            pending.offer(order);
        }

        public Order getOrder(String id) {
            return orders.get(id);
        }

        public void releasePartner(String partnerId) {
            partnerService.releasePartner(partnerId);
        }

        public void shutdown() {
            running = false;
            worker.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        PartnerService partners = new PartnerService();
        partners.addPartner("P1");

        AssignmentService service = new AssignmentService(partners);
        service.start();

        service.requestAssignment("O1");
        service.requestAssignment("O2");

        Thread.sleep(200);

        // O1 is assigned, O2 waits.
        System.out.println(service.getOrder("O1").status);
        System.out.println(service.getOrder("O2").status);

        // Simulate partner finishing O1.
        service.releasePartner("P1");

        Thread.sleep(200);

        System.out.println(service.getOrder("O2").status);

        service.shutdown();
    }
}
