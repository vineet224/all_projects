package practice.lld.thirditeration.cabridebooking;


import java.util.*;

// ===================== ENUMS =====================

enum RideStatus {
    REQUESTED,
    ASSIGNED,
    DRIVER_ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

// ===================== ENTITIES =====================

class Rider {
    final String id;
    final String name;

    Rider(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Driver {
    final String id;
    final String name;
    boolean available = true;

    Driver(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Ride {
    final String id;
    final String riderId;
    final String pickup;
    final String destination;

    String driverId;
    RideStatus status = RideStatus.REQUESTED;

    Ride(String id, String riderId,
         String pickup, String destination) {
        this.id = id;
        this.riderId = riderId;
        this.pickup = pickup;
        this.destination = destination;
    }

    void transitionTo(RideStatus next) {
        boolean valid = switch (status) {
            case REQUESTED ->
                    next == RideStatus.ASSIGNED ||
                            next == RideStatus.CANCELLED;

            case ASSIGNED ->
                    next == RideStatus.DRIVER_ARRIVED ||
                            next == RideStatus.CANCELLED;

            case DRIVER_ARRIVED ->
                    next == RideStatus.IN_PROGRESS ||
                            next == RideStatus.CANCELLED;

            case IN_PROGRESS ->
                    next == RideStatus.COMPLETED;

            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Invalid transition: " + status + " -> " + next
            );
        }

        status = next;
    }

    @Override
    public String toString() {
        return "Ride{id=" + id +
                ", rider=" + riderId +
                ", driver=" + driverId +
                ", status=" + status + "}";
    }
}

// ===================== MATCHING STRATEGY =====================

interface DriverMatchingStrategy {
    Driver findDriver(Collection<Driver> drivers);
}

class FirstAvailableDriverStrategy
        implements DriverMatchingStrategy {

    @Override
    public Driver findDriver(Collection<Driver> drivers) {
        for (Driver driver : drivers) {
            if (driver.available) {
                return driver;
            }
        }
        return null;
    }
}

// ===================== CAB BOOKING SERVICE =====================

class CabBookingService {

    private final Map<String, Rider> riders = new HashMap<>();
    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Ride> rides = new HashMap<>();

    private final DriverMatchingStrategy matchingStrategy;

    CabBookingService(DriverMatchingStrategy matchingStrategy) {
        this.matchingStrategy = matchingStrategy;
    }

    public synchronized void registerRider(Rider rider) {
        if (riders.containsKey(rider.id)) {
            throw new IllegalArgumentException("Rider exists");
        }
        riders.put(rider.id, rider);
    }

    public synchronized void registerDriver(Driver driver) {
        if (drivers.containsKey(driver.id)) {
            throw new IllegalArgumentException("Driver exists");
        }
        drivers.put(driver.id, driver);
    }

    public synchronized void setDriverAvailability(
            String driverId, boolean available) {

        Driver driver = getDriver(driverId);

        if (available && hasActiveRide(driverId)) {
            throw new IllegalStateException("Driver is on a ride");
        }

        driver.available = available;
    }

    // Request ride and immediately assign a driver
    public synchronized Ride requestRide(
            String riderId, String pickup, String destination) {

        if (!riders.containsKey(riderId)) {
            throw new IllegalArgumentException("Rider not found");
        }

        for (Ride ride : rides.values()) {
            if (ride.riderId.equals(riderId)
                    && isActive(ride)) {
                throw new IllegalStateException(
                        "Rider already has an active ride");
            }
        }

        Driver driver = matchingStrategy.findDriver(drivers.values());

        if (driver == null) {
            throw new IllegalStateException("No drivers available");
        }

        if (!driver.available) {
            throw new IllegalStateException("Driver unavailable");
        }

        Ride ride = new Ride(
                UUID.randomUUID().toString(),
                riderId, pickup, destination
        );

        ride.driverId = driver.id;
        ride.transitionTo(RideStatus.ASSIGNED);
        driver.available = false;

        rides.put(ride.id, ride);
        return ride;
    }

    public synchronized void driverArrived(String rideId) {
        Ride ride = getRide(rideId);
        ride.transitionTo(RideStatus.DRIVER_ARRIVED);
    }

    public synchronized void startRide(String rideId) {
        Ride ride = getRide(rideId);
        ride.transitionTo(RideStatus.IN_PROGRESS);
    }

    public synchronized void completeRide(String rideId) {
        Ride ride = getRide(rideId);
        ride.transitionTo(RideStatus.COMPLETED);

        Driver driver = getDriver(ride.driverId);
        driver.available = true;
    }

    public synchronized void cancelRide(String rideId) {
        Ride ride = getRide(rideId);
        ride.transitionTo(RideStatus.CANCELLED);

        if (ride.driverId != null) {
            Driver driver = getDriver(ride.driverId);
            driver.available = true;
        }
    }

    public synchronized Ride getRide(String rideId) {
        Ride ride = rides.get(rideId);

        if (ride == null) {
            throw new IllegalArgumentException("Ride not found");
        }
        return ride;
    }

    public synchronized List<Ride> getRideHistory(String riderId) {
        if (!riders.containsKey(riderId)) {
            throw new IllegalArgumentException("Rider not found");
        }

        List<Ride> result = new ArrayList<>();

        for (Ride ride : rides.values()) {
            if (ride.riderId.equals(riderId)) {
                result.add(ride);
            }
        }

        return result;
    }

    private Driver getDriver(String driverId) {
        Driver driver = drivers.get(driverId);

        if (driver == null) {
            throw new IllegalArgumentException("Driver not found");
        }
        return driver;
    }

    private boolean isActive(Ride ride) {
        return ride.status != RideStatus.COMPLETED
                && ride.status != RideStatus.CANCELLED;
    }

    private boolean hasActiveRide(String driverId) {
        for (Ride ride : rides.values()) {
            if (driverId.equals(ride.driverId) && isActive(ride)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {

        CabBookingService service = new CabBookingService(
                new FirstAvailableDriverStrategy()
        );

        service.registerRider(new Rider("R1", "Alice"));
        service.registerRider(new Rider("R2", "Bob"));

        service.registerDriver(new Driver("D1", "John"));
        service.registerDriver(new Driver("D2", "Mike"));

        Ride ride1 = service.requestRide(
                "R1", "Airport", "Railway Station"
        );
        System.out.println(ride1);

        Ride ride2 = service.requestRide(
                "R2", "Mall", "Office"
        );
        System.out.println(ride2);

        service.driverArrived(ride1.id);
        service.startRide(ride1.id);
        service.completeRide(ride1.id);

        System.out.println(ride1);

        // Driver from ride1 is available again
        Ride ride3 = service.requestRide(
                "R1", "Office", "Home"
        );

        System.out.println(ride3);

        service.cancelRide(ride3.id);
        System.out.println(ride3);

        System.out.println(service.getRideHistory("R1"));
    }

}


