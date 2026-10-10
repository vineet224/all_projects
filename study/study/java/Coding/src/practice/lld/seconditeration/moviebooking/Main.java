package practice.lld.seconditeration.moviebooking;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: MOVIE TICKET BOOKING (BookMyShow)
 * =======================================================================================
 * * 1. FUNCTIONAL REQUIREMENTS:
 * - Users can view available seats for a specific show.
 * - Users can select multiple seats and temporarily reserve them (e.g., for 10 mins).
 * - Users can complete payment to permanently book the seats.
 * - If payment fails or times out, seats must be freed for others.
 * * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - High Concurrency: Zero double-booking. ACID properties on seat allocation.
 * - Deadlock Prevention: Locking multiple seats simultaneously must not freeze threads.
 * * * 3. ASSUMPTIONS (State these upfront):
 * - We are designing the core booking engine for a single Show to fit the 45-min scope.
 * (Searching movies/theaters is simple CRUD and wastes interviews.interview time).
 * - Payment Gateway is mocked.
 * - In-memory TTL/Timeout for reservations is simulated via an ExecutorService or manual trigger.
 * * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Database Isolation: "In a real RDBMS, I would rely on 'SELECT ... FOR UPDATE' to
 * pessimistically lock the seat rows during the reservation transaction."
 * - Distributed Locks: "If scaling across multiple instances, I would use a Redis lock
 * with a TTL for the temporary reservation phase."
 * - Expiration/TTL: "To handle payment timeouts in production, I wouldn't use in-memory
 * threads. I'd use a Redis Key Expiry Event, a RabbitMQ Dead Letter Queue with a TTL,
 * or a cron job sweeping a 'pending_bookings' table."
 * =======================================================================================
 */

// --- 1. Enums & POJOs ---

enum SeatStatus {
    AVAILABLE,
    TEMPORARILY_RESERVED, // Locked while user is paying
    BOOKED                // Payment successful
}

enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED
}

class User {
    private final String userId;
    public User(String userId) { this.userId = userId; }
    public String getUserId() { return userId; }
}

// --- 2. Domain Models ---

class Seat {
    private final String seatId;
    private SeatStatus status;
    // Granular lock for THIS specific seat
    private final ReentrantLock lock;

    public Seat(String seatId) {
        this.seatId = seatId;
        this.status = SeatStatus.AVAILABLE;
        this.lock = new ReentrantLock();
    }

    public String getSeatId() { return seatId; }
    public SeatStatus getStatus() { return status; }
    public ReentrantLock getLock() { return lock; }

    public void setStatus(SeatStatus status) { this.status = status; }
}

class Booking {
    private final String bookingId;
    private final User user;
    private final List<Seat> seats;
    private BookingStatus status;

    public Booking(String bookingId, User user, List<Seat> seats) {
        this.bookingId = bookingId;
        this.user = user;
        this.seats = seats;
        this.status = BookingStatus.PENDING;
    }

    public String getBookingId() { return bookingId; }
    public List<Seat> getSeats() { return seats; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
}

// --- 3. Core Booking System ---

class ShowManager {
    private final String showId;
    private final Map<String, Seat> seats;
    private final Map<String, Booking> activeBookings;
    private final AtomicInteger bookingCounter = new AtomicInteger(1000);

    public ShowManager(String showId, int rows, int seatsPerRow) {
        this.showId = showId;
        this.seats = new ConcurrentHashMap<>();
        this.activeBookings = new ConcurrentHashMap<>();

        // Initialize seats (e.g., A1, A2, B1...)
        for (char row = 'A'; row < 'A' + rows; row++) {
            for (int i = 1; i <= seatsPerRow; i++) {
                String seatId = row + String.valueOf(i);
                seats.put(seatId, new Seat(seatId));
            }
        }
    }

    public void displayAvailableSeats() {
        long available = seats.values().stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        System.out.println("Show [" + showId + "] Available Seats: " + available);
    }

    /**
     * CORE LOGIC: Reserving multiple seats safely.
     */
    public Booking reserveSeats(User user, List<String> requestedSeatIds) {
        List<Seat> selectedSeats = new ArrayList<>();

        // 1. Validate existence
        for (String seatId : requestedSeatIds) {
            Seat seat = seats.get(seatId);
            if (seat == null) {
                System.out.println("❌ Error: Seat " + seatId + " does not exist.");
                return null;
            }
            selectedSeats.add(seat);
        }

        // 2. DEADLOCK PREVENTION: Always acquire locks in a consistent, sorted order.
        // If Thread A wants [A1, A2] and Thread B wants [A2, A1], sorting ensures
        // both try to lock A1 first, preventing a deadlock freeze.
        selectedSeats.sort(Comparator.comparing(Seat::getSeatId));

        List<Seat> lockedSeats = new ArrayList<>();
        boolean successfulReservation = true;

        try {
            // 3. Acquire all locks
            for (Seat seat : selectedSeats) {
                // Wait up to 2 seconds for a lock. If we can't get it, someone else is holding it.
                if (seat.getLock().tryLock(2, TimeUnit.SECONDS)) {
                    lockedSeats.add(seat);
                } else {
                    successfulReservation = false;
                    break;
                }
            }

            // 4. Check Availability of all locked seats
            if (successfulReservation) {
                for (Seat seat : lockedSeats) {
                    if (seat.getStatus() != SeatStatus.AVAILABLE) {
                        successfulReservation = false;
                        System.out.println("⚠️ Seat Collision: " + seat.getSeatId() + " is no longer available.");
                        break;
                    }
                }
            }

            // 5. Update State if everything is valid
            if (successfulReservation) {
                for (Seat seat : lockedSeats) {
                    seat.setStatus(SeatStatus.TEMPORARILY_RESERVED);
                }
                String bookingId = "BKG-" + bookingCounter.incrementAndGet();
                Booking booking = new Booking(bookingId, user, lockedSeats);
                activeBookings.put(bookingId, booking);

                System.out.println("⏳ PENDING: User " + user.getUserId() + " temporarily reserved seats " + requestedSeatIds + ". Waiting for payment...");
                return booking;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            successfulReservation = false;
        } finally {
            // 6. ALWAYS release locks in a finally block
            for (Seat seat : lockedSeats) {
                seat.getLock().unlock();
            }
        }

        return null; // Reservation failed
    }

    public boolean confirmPayment(String bookingId) {
        Booking booking = activeBookings.get(bookingId);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) {
            System.out.println("❌ Invalid or Expired Booking ID: " + bookingId);
            return false;
        }

        // We need to lock the seats again to permanently book them.
        // Since we know the user ALREADY holds a temporary reservation, the risk of
        // collision is low, but we still lock to be thread-safe.
        List<Seat> seatsToBook = booking.getSeats();
        seatsToBook.sort(Comparator.comparing(Seat::getSeatId)); // Deadlock prevention

        for (Seat seat : seatsToBook) { seat.getLock().lock(); }
        try {
            for (Seat seat : seatsToBook) {
                seat.setStatus(SeatStatus.BOOKED);
            }
            booking.setStatus(BookingStatus.CONFIRMED);
            System.out.println("✅ SUCCESS: Payment confirmed for Booking " + bookingId + ". Seats secured!");
            return true;
        } finally {
            for (Seat seat : seatsToBook) { seat.getLock().unlock(); }
        }
    }

    public void handlePaymentTimeout(String bookingId) {
        Booking booking = activeBookings.get(bookingId);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) return;

        List<Seat> seatsToRelease = booking.getSeats();
        seatsToRelease.sort(Comparator.comparing(Seat::getSeatId));

        for (Seat seat : seatsToRelease) { seat.getLock().lock(); }
        try {
            for (Seat seat : seatsToRelease) {
                seat.setStatus(SeatStatus.AVAILABLE); // Free the seats back up
            }
            booking.setStatus(BookingStatus.CANCELLED);
            System.out.println("⏰ TIMEOUT: Payment failed/timeout for Booking " + bookingId + ". Seats released.");
        } finally {
            for (Seat seat : seatsToRelease) { seat.getLock().unlock(); }
        }
    }
}

// --- 4. Main Execution & Concurrency Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING BOOKMYSHOW ===\n");
        // Create a small theater: 2 rows, 3 seats each (A1, A2, A3, B1, B2, B3)
        ShowManager show = new ShowManager("SHOW-AVENGERS", 2, 3);
        show.displayAvailableSeats();

        User alice = new User("Alice");
        User bob = new User("Bob");

        System.out.println("\n=== SCENARIO 1: THE RACE CONDITION (COLLISION) ===");
        // Alice and Bob try to book the exact same seats at the exact same millisecond
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable aliceTask = () -> {
            Booking b = show.reserveSeats(alice, Arrays.asList("A1", "A2"));
            if (b != null) show.confirmPayment(b.getBookingId());
        };

        Runnable bobTask = () -> {
            // Bob tries to steal A2!
            Booking b = show.reserveSeats(bob, Arrays.asList("A2", "A3"));
            if (b != null) show.confirmPayment(b.getBookingId());
        };

        executor.submit(aliceTask);
        executor.submit(bobTask);

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        System.out.println("\n=== SCENARIO 2: PAYMENT TIMEOUT ===");
        User charlie = new User("Charlie");
        Booking charlieBooking = show.reserveSeats(charlie, Arrays.asList("B1", "B2"));

        System.out.println("Charlie takes too long to pay...");
        // Simulate background TTL cleanup job
        show.handlePaymentTimeout(charlieBooking.getBookingId());

        System.out.println("\n=== SCENARIO 3: RECOVERY AFTER TIMEOUT ===");
        User dave = new User("Dave");
        System.out.println("Dave tries to book the seats Charlie lost...");
        Booking daveBooking = show.reserveSeats(dave, Arrays.asList("B1", "B2"));
        if (daveBooking != null) {
            show.confirmPayment(daveBooking.getBookingId());
        }

        System.out.println("\n=== FINAL THEATER STATE ===");
        show.displayAvailableSeats();
    }
}


/***
 * The Retrospective: Why this solution passes the Senior bar
 * The Deadlock Trap (Averted): Look at the selectedSeats.sort(Comparator.comparing(Seat::getSeatId)); line. In interviews, I deliberately check if a candidate remembers to order their resources before locking them. If you skip this, and user A books [A1, A2] while user B books [A2, A1], your system hangs forever. This one line proves you have 4+ YOE.
 *
 * State Machine vs. Holding Locks: Junior engineers will keep the ReentrantLock locked for the entire 10-minute payment window. That is horrific for performance. Here, we lock the seat just long enough to change its enum state to TEMPORARILY_RESERVED, then we release the lock immediately so other threads can safely read the state.
 *
 * Optimistic Pre-Validation: Notice how we use tryLock with a 2-second timeout instead of blocking endlessly. If the system is bogged down, we fail gracefully rather than taking down a Tomcat worker thread.
 */
