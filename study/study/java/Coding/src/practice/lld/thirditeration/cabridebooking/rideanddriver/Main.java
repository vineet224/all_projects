package practice.lld.thirditeration.cabridebooking.rideanddriver;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

enum RideStatus {
    REQUESTED, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
}

class Driver {
    final String id;

    Driver(String id) {
        this.id = id;
    }
}

class Ride {
    final String id = UUID.randomUUID().toString();
    final String riderId;
    volatile String driverId;
    volatile RideStatus status = RideStatus.REQUESTED;

    Ride(String riderId) {
        this.riderId = riderId;
    }
}

class RideDispatcher implements AutoCloseable {
    private final BlockingQueue<Ride> pendingRides =
            new LinkedBlockingQueue<>();

    private final BlockingQueue<Driver> availableDrivers =
            new LinkedBlockingQueue<>();

    private final Map<String, Ride> rides =
            new ConcurrentHashMap<>();

    private final Map<String, Driver> drivers =
            new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    RideDispatcher() {
        worker = new Thread(this::dispatchLoop);
        worker.start();
    }

    public Ride requestRide(String riderId) {
        if (!running.get()) {
            throw new IllegalStateException("Dispatcher stopped");
        }

        Ride ride = new Ride(riderId);
        rides.put(ride.id, ride);
        pendingRides.add(ride);

        return ride; // Returns immediately: REQUESTED
    }

    public void registerDriver(String driverId) {
        Driver driver = new Driver(driverId);

        if (drivers.putIfAbsent(driverId, driver) != null) {
            throw new IllegalArgumentException("Driver already exists");
        }

        availableDrivers.add(driver);
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                // Blocks until a ride arrives
                Ride ride = pendingRides.take();

                // Skip rides cancelled while waiting
                if (ride.status != RideStatus.REQUESTED) {
                    continue;
                }

                // Blocks until a driver becomes available
                Driver driver = availableDrivers.take();

                synchronized (ride) {
                    if (ride.status == RideStatus.REQUESTED) {
                        ride.driverId = driver.id;
                        ride.status = RideStatus.ASSIGNED;

                        System.out.println(
                                "Assigned " + driver.id +
                                        " to ride " + ride.id
                        );
                    } else {
                        // Cancelled while waiting for a driver
                        availableDrivers.add(driver);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void cancelRide(String rideId) {
        Ride ride = getRide(rideId);

        synchronized (ride) {
            if (ride.status != RideStatus.REQUESTED) {
                throw new IllegalStateException(
                        "Only pending rides can be cancelled");
            }

            ride.status = RideStatus.CANCELLED;
        }

        // If already taken by worker, worker checks status
        pendingRides.remove(ride);
    }

    public void startRide(String rideId) {
        Ride ride = getRide(rideId);

        synchronized (ride) {
            if (ride.status != RideStatus.ASSIGNED) {
                throw new IllegalStateException("Invalid state");
            }
            ride.status = RideStatus.IN_PROGRESS;
        }
    }

    public void completeRide(String rideId) {
        Ride ride = getRide(rideId);

        synchronized (ride) {
            if (ride.status != RideStatus.IN_PROGRESS) {
                throw new IllegalStateException("Invalid state");
            }

            ride.status = RideStatus.COMPLETED;

            // Return driver to available pool
            availableDrivers.add(drivers.get(ride.driverId));
        }
    }

    public Ride getRide(String rideId) {
        Ride ride = rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found");
        }
        return ride;
    }

    @Override
    public void close() {
        running.set(false);
        worker.interrupt();
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        try (RideDispatcher service = new RideDispatcher()) {
            Ride r1 = service.requestRide("R1");
            Ride r2 = service.requestRide("R2");

            // Both rides initially pending
            service.registerDriver("D1");

            // Wait only to demonstrate asynchronous assignment
            Thread.sleep(100);

            System.out.println(r1.status); // ASSIGNED
            System.out.println(r2.status); // REQUESTED

            service.startRide(r1.id);
            service.completeRide(r1.id);

            Thread.sleep(100);

            System.out.println(r2.status); // ASSIGNED
        }
    }
}
