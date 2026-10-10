package practice.lld.firstiteration.simplelld;

import java.time.LocalDateTime;

class Pair{
    int cost;
    String value;
    public Pair(int cost, String value){
        this.cost = cost;
        this.value = value;
    }
}

class Bill{
    int id;
    int ticketId;
    int totacost;
    LocalDateTime outTime;
}

interface BillingInterface{
    public Bill createBill(String ticketId);
}

enum VehicleType {
    TWO_WHEELER,
    FOUR_WHEELER
}

class ParkingTicket{
    int id;
    int userid;
    int slotid;
    LocalDateTime incomingTime;
}


public class SimpleLLD {

    // here the main mappings can be stored and other data



    public static void main(String[] args){
        System.out.println(" the value is "+ 3);
    }
}


/***
 * slot{slot_id, status,slot_no}
 * ticket{ticketId, status, slotid, vehicleId, starttime}
 * parkinglotmanager{
 *     // hashmap<slotid,slot></>
 *     // hashmap<ticketId,ticket></>
 *     //
 * }
 */