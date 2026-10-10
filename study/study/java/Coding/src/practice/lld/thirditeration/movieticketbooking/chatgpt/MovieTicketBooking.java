package practice.lld.thirditeration.movieticketbooking.chatgpt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}

enum BookingStatus {
    PAYMENT_PENDING,
    BOOKED,
    FAILED
}

record SeatKey(String showId, String seatId) {}

class ShowSeat {

    final String showId;
    final String seatId;

    volatile SeatStatus status = SeatStatus.AVAILABLE;

    String heldByBookingId;

    final ReentrantLock lock = new ReentrantLock();

    ShowSeat(String showId, String seatId) {
        this.showId = showId;
        this.seatId = seatId;
    }
}

class Booking {

    final String bookingId;
    final String showId;
    final List<String> seatIds;

    BookingStatus status = BookingStatus.PAYMENT_PENDING;

    Booking(String bookingId, String showId, List<String> seatIds) {
        this.bookingId = bookingId;
        this.showId = showId;
        this.seatIds = new ArrayList<>(seatIds);
    }
}

public class MovieTicketBooking {

    private final ConcurrentHashMap<SeatKey, ShowSeat> seats =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Booking> bookings =
            new ConcurrentHashMap<>();


    // ---------------- ADD SEAT ----------------

    public void addSeat(String showId, String seatId) {

        SeatKey key = new SeatKey(showId, seatId);

        seats.put(key, new ShowSeat(showId, seatId));
    }


    // ---------------- GET AVAILABLE SEATS ----------------

    public List<ShowSeat> getAvailableSeats(String showId) {

        List<ShowSeat> result = new ArrayList<>();

        for (ShowSeat seat : seats.values()) {

            if (seat.showId.equals(showId)
                    && seat.status == SeatStatus.AVAILABLE) {

                result.add(seat);
            }
        }

        return result;
    }


    // ---------------- CHECKOUT ----------------

    public Booking checkout(
            String bookingId,
            String showId,
            List<String> seatIds) {

        Booking booking =
                new Booking(bookingId, showId, seatIds);

        if (bookings.putIfAbsent(bookingId, booking) != null) {
            throw new RuntimeException("Duplicate booking");
        }

        // Global ordering prevents deadlock
        List<String> sortedSeatIds = new ArrayList<>(seatIds);
        Collections.sort(sortedSeatIds);

        List<ShowSeat> lockedSeats = new ArrayList<>();

        try {

            // 1. Lock + validate every seat
            for (String seatId : sortedSeatIds) {

                ShowSeat seat =
                        seats.get(new SeatKey(showId, seatId));

                if (seat == null) {
                    throw new RuntimeException(
                            "Seat not found: " + seatId);
                }

                seat.lock.lock();
                lockedSeats.add(seat);

                // IMPORTANT: validation happens AFTER lock
                if (seat.status != SeatStatus.AVAILABLE) {
                    throw new RuntimeException(
                            "Seat unavailable: " + seatId);
                }
            }

            // 2. We own all locks, so reserve all seats
            for (ShowSeat seat : lockedSeats) {
                seat.status = SeatStatus.HELD;
                seat.heldByBookingId = bookingId;
            }

            return booking;

        } catch (Exception e) {

            booking.status = BookingStatus.FAILED;
            throw e;

        } finally {

            // unlock only locks actually acquired
            for (ShowSeat seat : lockedSeats) {
                seat.lock.unlock();
            }
        }
    }


    // ---------------- PAYMENT SUCCESS WEBHOOK ----------------

    public void paymentSuccess(String bookingId) {

        Booking booking = bookings.get(bookingId);

        if (booking == null) {
            throw new RuntimeException("Booking not found");
        }

        synchronized (booking) {

            // idempotent webhook
            if (booking.status == BookingStatus.BOOKED) {
                return;
            }

            if (booking.status != BookingStatus.PAYMENT_PENDING) {
                throw new RuntimeException(
                        "Invalid booking state");
            }

            List<String> sortedSeatIds =
                    new ArrayList<>(booking.seatIds);

            Collections.sort(sortedSeatIds);

            List<ShowSeat> lockedSeats = new ArrayList<>();

            try {

                for (String seatId : sortedSeatIds) {

                    ShowSeat seat = seats.get(
                            new SeatKey(
                                    booking.showId,
                                    seatId));

                    seat.lock.lock();
                    lockedSeats.add(seat);

                    if (seat.status != SeatStatus.HELD ||
                            !bookingId.equals(seat.heldByBookingId)) {

                        throw new RuntimeException(
                                "Seat not held by this booking");
                    }
                }

                for (ShowSeat seat : lockedSeats) {
                    seat.status = SeatStatus.BOOKED;
                }

                booking.status = BookingStatus.BOOKED;

            } finally {

                for (ShowSeat seat : lockedSeats) {
                    seat.lock.unlock();
                }
            }
        }
    }
}