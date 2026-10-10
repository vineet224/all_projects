package practice.lld.seconditeration.parkinglotuserallocation;


import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD: PARKING LOT (USER-SELECTED SLOT VARIANT)
 * =======================================================================================
 * * FUNCTIONAL REQUIREMENTS:
 * - User explicitly provides the target Slot ID they want to park in.
 * - System must validate if the slot exists, matches vehicle type, and is free.
 * * * NON-FUNCTIONAL REQUIREMENTS:
 * - High collision handling: Multiple users trying to book the same slot must be safely
 * rejected without corrupting state.
 * * * HLD CALLOUTS (For the Interviewer):
 * - "By allowing users to pick slots, we introduce high write-contention on popular slots.
 * In this code, I'm handling this with pessimistic locking via ReentrantLocks per slot.
 * In a distributed DB, I would use an Optimistic Lock (Versioning) if collisions are rare,
 * or a Pessimistic Lock (SELECT FOR UPDATE) if collisions are frequent."
 * =======================================================================================
 */

// --- Enums & POJOs ---
enum VehicleType { TWO_WHEELER, FOUR_WHEELER }

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
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = LocalDateTime.now();
    }
    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public Slot getSlot() { return slot; }
    public LocalDateTime getEntryTime() { return entryTime; }
}

// --- Domain Models ---
class Slot {
    private final String slotId;
    private final VehicleType supportedType;
    private boolean isOccupied;
    // Granular lock to handle collisions on THIS specific slot
    private final ReentrantLock lock;

    public Slot(String slotId, VehicleType supportedType) {
        this.slotId = slotId;
        this.supportedType = supportedType;
        this.isOccupied = false;
        this.lock = new ReentrantLock();
    }

    public String getSlotId() { return slotId; }
    public VehicleType getSupportedType() { return supportedType; }
    public boolean isOccupied() { return isOccupied; }

    // Core Concurrency Logic: First thread to get the lock wins.
    public boolean occupy() {
        lock.lock();
        try {
            if (!isOccupied) {
                isOccupied = true;
                return true;
            }
            return false; // Slot was taken by another thread a millisecond ago
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

// --- Core System ---
class ParkingLotManager {
    private static ParkingLotManager instance;

    // Single source of truth for all slots. O(1) lookup.
    private final Map<String, Slot> allSlots;
    private final Map<String, Ticket> activeTickets;

    private ParkingLotManager() {
        this.allSlots = new ConcurrentHashMap<>();
        this.activeTickets = new ConcurrentHashMap<>();
    }

    public static ParkingLotManager getInstance() {
        if (instance == null) {
            synchronized (ParkingLotManager.class) {
                if (instance == null) {
                    instance = new ParkingLotManager();
                }
            }
        }
        return instance;
    }

    public void addSlot(Slot slot) {
        allSlots.put(slot.getSlotId(), slot);
    }

    // NEW LOGIC: User provides the target Slot ID
    public Ticket parkVehicleAtSlot(Vehicle vehicle, String targetSlotId) {
        Slot slot = allSlots.get(targetSlotId);

        // 1. Validation Checks
        if (slot == null) {
            System.out.println("❌ Invalid Slot: " + targetSlotId + " does not exist.");
            return null;
        }
        if (slot.getSupportedType() != vehicle.getType()) {
            System.out.println("❌ Type Mismatch: " + vehicle.getLicensePlate() +
                    " cannot park in a " + slot.getSupportedType() + " slot.");
            return null;
        }

        // 2. Concurrency Check (The Collision Zone)
        if (slot.occupy()) {
            Ticket ticket = new Ticket(vehicle, slot);
            activeTickets.put(ticket.getTicketId(), ticket);
            System.out.println("✅ Parked: " + vehicle.getLicensePlate() + " securely locked slot " + slot.getSlotId());
            return ticket;
        } else {
            // 3. Collision handling (User must be told to pick another slot)
            System.out.println("⚠️ Collision: " + vehicle.getLicensePlate() +
                    " failed. Slot " + targetSlotId + " is already occupied. Please pick another.");
            return null;
        }
    }

    public void unparkVehicle(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Invalid Ticket ID");
        }

        Slot slot = ticket.getSlot();
        slot.free();
        System.out.println("🚗 Exited: " + ticket.getVehicle().getLicensePlate() + " | Slot freed: " + slot.getSlotId());
    }
}

// --- Main Execution & Collision Test ---
public class ParkingLotWithUserAllocation {
    public static void main(String[] args) throws InterruptedException {
        ParkingLotManager lot = ParkingLotManager.getInstance();

        // Add standard slots
        lot.addSlot(new Slot("VIP-1", VehicleType.FOUR_WHEELER));
        lot.addSlot(new Slot("REG-1", VehicleType.FOUR_WHEELER));

        System.out.println("=== TESTING EXTREME COLLISION ON A SINGLE SLOT ===");
        // Simulate 5 cars trying to book "VIP-1" at the exact same time
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger plateCounter = new AtomicInteger(100);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                String plate = "KA-" + plateCounter.incrementAndGet();
                Vehicle car = new Vehicle(plate, VehicleType.FOUR_WHEELER);
                // ALL 5 threads target VIP-1 simultaneously
                lot.parkVehicleAtSlot(car, "VIP-1");
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n=== TESTING VALIDATION RULES ===");
        Vehicle bike = new Vehicle("BIKE-99", VehicleType.TWO_WHEELER);
        // Fail 1: Wrong Type
        lot.parkVehicleAtSlot(bike, "REG-1");
        // Fail 2: Non-existent slot
        lot.parkVehicleAtSlot(bike, "GHOST-SLOT");
    }
}
