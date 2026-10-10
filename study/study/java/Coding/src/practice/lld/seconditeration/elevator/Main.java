package practice.lld.seconditeration.elevator;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: ELEVATOR SYSTEM (POLLING + TICK-BASED LOOK ALGORITHM)
 * =======================================================================================
 * * HLD CALLOUT (For the Interviewer):
 * "To keep the code clean and language-agnostic for this 45-min round, I am using a
 * 500ms polling sleep when the elevator is idle. In a real production environment,
 * I would use a `Condition` (await/signal) or a Blocking Queue to put the idle
 * thread to sleep and completely prevent CPU busy-waiting."
 * =======================================================================================
 */

enum Direction { UP, DOWN, IDLE }

class Elevator implements Runnable {
    public final int id;
    private int currentFloor;
    private Direction direction;

    // TreeSet natively keeps floors sorted for the LOOK algorithm.
    private final TreeSet<Integer> stops;
    private final ReentrantLock lock;
    private volatile boolean isRunning;

    public Elevator(int id, int startingFloor) {
        this.id = id;
        this.currentFloor = startingFloor;
        this.direction = Direction.IDLE;
        this.stops = new TreeSet<>();
        this.lock = new ReentrantLock();
        this.isRunning = true;
    }

    // INTERNAL REQUEST
    public void requestInternal(int targetFloor) {
        addStop(targetFloor, "Internal");
    }

    // Safely add a stop
    protected void addStop(int floor, String source) {
        lock.lock();
        try {
            stops.add(floor);
            System.out.println("🔘 [" + source + "] Request added for Floor " + floor + " to Elevator " + id);

            // If idle, set the direction so the polling loop knows which way to start
            if (direction == Direction.IDLE) {
                direction = (floor > currentFloor) ? Direction.UP : Direction.DOWN;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        System.out.println("🚀 Elevator " + id + " started at floor " + currentFloor);

        while (isRunning) {
            boolean isIdle = false;
            boolean doorsOpening = false;

            lock.lock();
            try {
                // PHASE 1: Check if empty
                if (stops.isEmpty() && isRunning) {
                    direction = Direction.IDLE;
                    isIdle = true;
                }
                // PHASE 2: Check if we need to stop at the CURRENT floor
                else if (stops.contains(currentFloor)) {
                    System.out.println("   🚪 Doors Opening at Floor " + currentFloor + " (Elevator " + id + ")");
                    stops.remove(currentFloor);

                    // Recalculate direction using TreeSet higher/lower logic
                    if (direction == Direction.UP && stops.higher(currentFloor) == null) {
                        direction = Direction.DOWN;
                    } else if (direction == Direction.DOWN && stops.lower(currentFloor) == null) {
                        direction = Direction.UP;
                    }
                    doorsOpening = true;
                }
            } finally {
                // ALWAYS unlock before sleeping so users can press buttons!
                lock.unlock();
            }

            // PHASE 3: Execute Time-Consuming Actions (Outside the lock)
            try {
                if (isIdle) {
                    // THE POLLING WAIT: Replaces Condition.await()
                    Thread.sleep(500);
                    continue;
                }

                if (doorsOpening) {
                    Thread.sleep(1000); // Simulate Door Open/Close time
                    System.out.println("   🚪 Doors Closing at Floor " + currentFloor);
                    continue;
                }

                // Move EXACTLY ONE floor in the current direction
                Thread.sleep(500); // Travel time per floor
                currentFloor += (direction == Direction.UP) ? 1 : -1;
                System.out.println("⬆️⬇️ Elevator " + id + " passing Floor " + currentFloor);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        isRunning = false;
    }
}

// --- Elevator Manager (The Router) ---
class ElevatorManager {
    private final List<Elevator> elevators;

    public ElevatorManager(int numberOfElevators) {
        this.elevators = new ArrayList<>();
        for (int i = 0; i < numberOfElevators; i++) {
            Elevator e = new Elevator(i, 0);
            elevators.add(e);
            new Thread(e).start();
        }
    }

    public Elevator getElevator(int id) {
        return elevators.get(id);
    }

    // EXTERNAL REQUEST
    public void requestExternal(int sourceFloor, Direction requestedDirection) {
        // Dumb routing: Just pick Elevator 0 for the interviews.interview.
        Elevator chosenElevator = elevators.get(0);
        chosenElevator.addStop(sourceFloor, "External - Hallway");
    }

    public void shutdownAll() {
        for (Elevator e : elevators) e.shutdown();
    }
}

// --- Main Execution ---
public class Main {
    public static void main(String[] args) throws InterruptedException {
        ElevatorManager manager = new ElevatorManager(1);
        Elevator e0 = manager.getElevator(0);

        Thread.sleep(500);

        System.out.println("\n=== SCENARIO 1: EXTERNAL REQUEST ===");
        manager.requestExternal(5, Direction.DOWN);

        Thread.sleep(1000);

        System.out.println("\n=== SCENARIO 2: INTERCEPTING AN INTERNAL REQUEST ===");
        e0.requestInternal(3);

        Thread.sleep(6000);

        System.out.println("\n=== SCENARIO 3: FLIPPING DIRECTIONS ===");
        e0.requestInternal(1);

        Thread.sleep(3000);

        manager.shutdownAll();
        System.out.println("System Shut Down.");
    }
}