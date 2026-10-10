package practice.lld.seconditeration.parkinglotSystemAllocation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: PARKING LOT (Mid/Senior Level)
 * =======================================================================================
 * * 1. FUNCTIONAL REQUIREMENTS:
 * - Support multiple vehicle types (TWO_WHEELER, FOUR_WHEELER).
 * - Park vehicle and assign an available slot.
 * - Unpark vehicle, free the slot, and calculate the fee.
 * - Support dynamic pricing based on vehicle type and duration.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: High volume of concurrent entries/exits MUST NOT double-book slots.
 * - Low Latency: Finding a free slot should be O(1), not O(N) iteration.
 * * 3. ASSUMPTIONS (State these upfront to save time):
 * - Single entry and single exit point (simplifies the scope for a 45-min interviews.interview).
 * - In-memory data structures are sufficient; no persistence required.
 * - Pricing is flat-rate per hour. Fractional hours are rounded up.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Distributed Locks: "I am using ReentrantLocks and ConcurrentQueues here. In a distributed
 * system with multiple gates, I'd use Redis distributed locks or rely on a relational
 * DB's `SELECT FOR UPDATE` (Pessimistic Locking) to avoid double booking."
 * - Data Consistency: "Instead of querying live transactional tables to show 'Available
 * Slots' on a digital board, I'd maintain an async Redis counter or materialized view
 * to handle the high read throughput."
 * - Rule Engine: "I used the Strategy Pattern for pricing. In production, this would be
 * powered by a Rule Engine (like Drools) reading configs from a DB so we don't
 * require code deployments for pricing changes."
 * =======================================================================================
 */

// --- 1. Enums & POJOs ---

enum VehicleType {
    TWO_WHEELER, FOUR_WHEELER
}

class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public VehicleType getType() { return type; }
    public String getLicensePlate() { return licensePlate; }
}

class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final Slot slot;
    private final LocalDateTime entryTime;

    public Ticket(Vehicle vehicle, Slot slot) {
        // Use UUID or AtomicInteger for unique ticket IDs
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = LocalDateTime.now();
    }

    // Overloaded constructor specifically for testing pricing with simulated past entry times
    public Ticket(Vehicle vehicle, Slot slot, LocalDateTime simulatedEntryTime) {
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = simulatedEntryTime;
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public Slot getSlot() { return slot; }
    public LocalDateTime getEntryTime() { return entryTime; }
}

// --- 2. Domain Models ---

class Slot {
    private final String slotId;
    private final VehicleType supportedType;
    private boolean isOccupied;
    // Fine-grained locking per slot. Avoids locking the entire parking lot.
    private final ReentrantLock lock;

    public Slot(String slotId, VehicleType supportedType) {
        this.slotId = slotId;
        this.supportedType = supportedType;
        this.isOccupied = false;
        this.lock = new ReentrantLock();
    }

    public String getSlotId() { return slotId; }
    public VehicleType getSupportedType() { return supportedType; }

    public boolean occupy() {
        lock.lock();
        try {
            if (!isOccupied) {
                isOccupied = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void free() {
        lock.lock();
        try {
            isOccupied = false;
        } finally {
            lock.unlock();
        }
    }
}

// --- 3. Strategies ---

interface PricingStrategy {
    double calculatePrice(Ticket ticket, LocalDateTime exitTime);
}

class HourlyPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(Ticket ticket, LocalDateTime exitTime) {
        long hours = Duration.between(ticket.getEntryTime(), exitTime).toHours();
        // Charge for minimum 1 hour, round up for partial hours
        hours = hours == 0 ? 1 : hours + 1;

        if (ticket.getVehicle().getType() == VehicleType.TWO_WHEELER) {
            return hours * 10.0;
        } else if (ticket.getVehicle().getType() == VehicleType.FOUR_WHEELER) {
            return hours * 20.0;
        }
        throw new IllegalArgumentException("Unknown vehicle type");
    }
}

// --- 4. Core System ---

class ParkingLotManager {
    private static ParkingLotManager instance;

    // Fast O(1) lookup for free slots using Concurrent Queues. Far better than iterating a list.
    private final Map<VehicleType, ConcurrentLinkedQueue<Slot>> freeSlots;

    // Ledger of active tickets
    private final Map<String, Ticket> activeTickets;

    private final PricingStrategy pricingStrategy;

    private ParkingLotManager(PricingStrategy pricingStrategy) {
        this.pricingStrategy = pricingStrategy;
        this.freeSlots = new ConcurrentHashMap<>();
        this.freeSlots.put(VehicleType.TWO_WHEELER, new ConcurrentLinkedQueue<>());
        this.freeSlots.put(VehicleType.FOUR_WHEELER, new ConcurrentLinkedQueue<>());
        this.activeTickets = new ConcurrentHashMap<>();
    }

    // Singleton Pattern with Double-Checked Locking for thread-safe initialization
    public static ParkingLotManager getInstance(PricingStrategy strategy) {
        if (instance == null) {
            synchronized (ParkingLotManager.class) {
                if (instance == null) {
                    instance = new ParkingLotManager(strategy);
                }
            }
        }
        return instance;
    }

    public void addSlot(Slot slot) {
        freeSlots.get(slot.getSupportedType()).offer(slot);
    }

    public Ticket parkVehicle(Vehicle vehicle) {
        ConcurrentLinkedQueue<Slot> queue = freeSlots.get(vehicle.getType());
        Slot slot = queue.poll(); // O(1) removal of an available slot

        if (slot == null) {
            System.out.println("❌ Lot Full: No available slots for " + vehicle.getType() + " [" + vehicle.getLicensePlate() + "]");
            return null;
        }

        // Lock and occupy the slot
        if (slot.occupy()) {
            Ticket ticket = new Ticket(vehicle, slot);
            activeTickets.put(ticket.getTicketId(), ticket);
            System.out.println("✅ Parked: " + vehicle.getLicensePlate() + " at " + slot.getSlotId() + " | Ticket: " + ticket.getTicketId());
            return ticket;
        } else {
            // Edge case: Slot was magically occupied (shouldn't happen with single queue, but defensively handle)
            System.out.println("⚠️ Collision: Slot " + slot.getSlotId() + " was occupied.");
            return null;
        }
    }

    // Overloaded method specifically for testing with simulated back-in-time entries
    public Ticket parkVehicleTest(Vehicle vehicle, int hoursBack) {
        ConcurrentLinkedQueue<Slot> queue = freeSlots.get(vehicle.getType());
        Slot slot = queue.poll();
        if (slot != null && slot.occupy()) {
            Ticket ticket = new Ticket(vehicle, slot, LocalDateTime.now().minusHours(hoursBack).minusMinutes(30));
            activeTickets.put(ticket.getTicketId(), ticket);
            return ticket;
        }
        return null;
    }

    public double unparkVehicle(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Invalid or already processed Ticket ID: " + ticketId);
        }

        Slot slot = ticket.getSlot();
        double fee = pricingStrategy.calculatePrice(ticket, LocalDateTime.now());

        slot.free();
        // Add the slot back to the available pool
        freeSlots.get(slot.getSupportedType()).offer(slot);

        System.out.println("🚗 Exited: " + ticket.getVehicle().getLicensePlate() +
                " | Slot freed: " + slot.getSlotId() +
                " | Total Fee: ₹" + fee);
        return fee;
    }
}

// --- 5. Main Execution & Concurrency Test ---

public class ParkingLotWithSystemAllocation {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING PARKING LOT ===\n");
        ParkingLotManager lot = ParkingLotManager.getInstance(new HourlyPricingStrategy());

        // Add 2 Two-Wheeler slots and 2 Four-Wheeler slots
        lot.addSlot(new Slot("2W-01", VehicleType.TWO_WHEELER));
        lot.addSlot(new Slot("2W-02", VehicleType.TWO_WHEELER));
        lot.addSlot(new Slot("4W-01", VehicleType.FOUR_WHEELER));
        lot.addSlot(new Slot("4W-02", VehicleType.FOUR_WHEELER));

        System.out.println("=== TESTING CONCURRENCY ===");
        // Simulate 10 cars arriving at the exact same time looking for 2 Four-Wheeler slots
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger plateCounter = new AtomicInteger(1000);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                String plate = "KA-01-" + plateCounter.incrementAndGet();
                Vehicle car = new Vehicle(plate, VehicleType.FOUR_WHEELER);
                lot.parkVehicle(car);
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n=== TESTING PRICING & RE-ENTRY ===");
        // Park a Two-Wheeler 3.5 hours ago
        Vehicle bike1 = new Vehicle("KA-05-BIKE1", VehicleType.TWO_WHEELER);
        Ticket bikeTicket = lot.parkVehicleTest(bike1, 3); // 3 hours 30 mins ago

        if (bikeTicket != null) {
            System.out.println("Processing exit for bike...");
            // Should charge for 4 hours (4 * 10 = ₹40)
            lot.unparkVehicle(bikeTicket.getTicketId());
        }

        // Show that a previously failed car can now park if we add a slot or free a slot
        System.out.println("\nFreeing a 4W slot manually to simulate departure...");
        lot.addSlot(new Slot("4W-03", VehicleType.FOUR_WHEELER));
        Vehicle delayedCar = new Vehicle("KA-01-DELAYED", VehicleType.FOUR_WHEELER);
        lot.parkVehicle(delayedCar);
    }
}