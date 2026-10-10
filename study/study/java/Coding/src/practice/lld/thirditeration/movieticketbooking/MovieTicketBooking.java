package practice.lld.thirditeration.movieticketbooking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * functional requirement
 * - user should be able to book multiple seats in a single booking
 * -
 */

class Show{
    String showId;
    String movieName;
    public Show(String showId,String movieName){
        this.movieName=movieName;
        this.showId=showId;
    }
}

enum SeatStatus{CheckedOut,Booked,Aailable}
enum TicketStatus{CheckedOut,Booked,Failed}

class ShowSeat{
    String showId;
    String seatId;
    SeatStatus isBooked;
    ReentrantLock lock=new ReentrantLock();
    public ShowSeat(String showId, String seatId) {
        this.showId = showId;
        this.seatId = seatId;
        this.isBooked = SeatStatus.Aailable;
    }

    public void  setSeatStatus(SeatStatus isBooked) {
        this.isBooked = isBooked;
    }
}

class Ticket{
    String ticketId;
    ArrayList<String> seatIds;
    TicketStatus  ticketStatus;
    public Ticket(String ticketId,ArrayList<String> seatIds){
        this.ticketId=ticketId;
        this.seatIds=seatIds;
        this.ticketStatus=TicketStatus.CheckedOut;
    }

    public synchronized  void  setTicketStatus(TicketStatus ticketStatus){
        this.ticketStatus=ticketStatus;
    }
    public synchronized  TicketStatus getTicketStatus(){
        return this.ticketStatus;
    }

}

public class MovieTicketBooking {

    ConcurrentHashMap<String,Ticket> allTickets=new ConcurrentHashMap<>();
    ConcurrentHashMap<String,ShowSeat> allShowSeats=new ConcurrentHashMap<>();

    public MovieTicketBooking(){
        ShowSeat showSeat=new ShowSeat("show1","showseat1");
        ShowSeat showSeat2=new ShowSeat("show2","showseat1");
        ShowSeat showSeat3=new ShowSeat("show1","showseat2");
        ShowSeat showSeat4=new ShowSeat("show1","showseat3");
        allShowSeats.put(showSeat.seatId,showSeat);
        allShowSeats.put(showSeat2.seatId,showSeat2);
        allShowSeats.put(showSeat3.seatId,showSeat3);
        allShowSeats.put(showSeat4.seatId,showSeat4);
    }

    public ArrayList<ShowSeat> getAvailableShowSeats(String showId){
        ArrayList<ShowSeat> showSeats=new ArrayList<>();
        for(ShowSeat showSeat:allShowSeats.values()){
            if(showSeat.showId.equals(showId) && showSeat.isBooked.equals(SeatStatus.Aailable)){
                showSeats.add(showSeat);
            }
        }
        return showSeats;
    }

    public TicketStatus checkoutTicket(Ticket ticket){
        if(allTickets.putIfAbsent(ticket.ticketId, ticket)!=null){
            System.out.println("double booking ticket");
            throw new RuntimeException("Can not process double booking");
        }

        Collections.sort(ticket.seatIds);
        try{
            for(String seatid:ticket.seatIds){
                ShowSeat showSeat = allShowSeats.get(seatid);
                if(!showSeat.isBooked.equals(SeatStatus.Aailable)){
                    throw new IllegalArgumentException(" the seatId "+seatid+" is not avaialble for checkout for ticket"+ticket.ticketId);
                }
                showSeat.lock.lock();
            }
            for(String seatId:ticket.seatIds){
                ShowSeat  showSeat = allShowSeats.get(seatId);
                showSeat.isBooked=SeatStatus.CheckedOut;
            }
        }catch(Exception e){
            ticket.setTicketStatus(TicketStatus.Failed);
            throw new RuntimeException("Exception while checkout action");
        }finally {
            for(String seatid:ticket.seatIds){
                ShowSeat showSeat = allShowSeats.get(seatid);
                showSeat.lock.unlock();
            }
        }

        ticket.setTicketStatus(TicketStatus.CheckedOut);
        return ticket.getTicketStatus();
    }

    public TicketStatus confirmationWebhook(String ticketId){
        if(!allTickets.containsKey(ticketId)){
            throw new RuntimeException("Ticket id "+ticketId+" not found");
        }
        Ticket ticket=allTickets.get(ticketId);
        if(!ticket.ticketStatus.equals(TicketStatus.CheckedOut)){
            throw new RuntimeException("Ticket status is laready modified "+ticket.ticketStatus.name());
        }

        Collections.sort(ticket.seatIds);
        try{
            for(String seatid:ticket.seatIds){
                ShowSeat showSeat = allShowSeats.get(seatid);
                if(!showSeat.isBooked.equals(SeatStatus.CheckedOut)){
                    throw new IllegalArgumentException(" the seatId "+seatid+" is not checkout for checkout for ticket"+ticket.ticketId);
                }
                showSeat.lock.lock();
            }
            for(String seatId:ticket.seatIds){
                ShowSeat  showSeat = allShowSeats.get(seatId);
                showSeat.isBooked=SeatStatus.Booked;
            }
        }catch(Exception e){
            ticket.setTicketStatus(TicketStatus.Failed);
            throw new RuntimeException("Exception while confirmation action");
        }finally {
            for(String seatid:ticket.seatIds){
                ShowSeat showSeat = allShowSeats.get(seatid);
                showSeat.lock.unlock();
            }
        }

        ticket.setTicketStatus(TicketStatus.Booked);
        return ticket.getTicketStatus();
    }
}
