package practice.lld.firstiteration.alllld;


/***
 * 1 inventroy management : {parking lot, movie ticket, amazon item store}
 * 2 user wallet managment : {stock trading system broker only , splitwise, }
 * 3 state management: ATM machine , coffee vendor, elevator ,
 * 4 filtering query on data : logger, database design,
 * 5 pre math logical : rate limit , splitwise settlement, trading stock allocation ,
 * 6 kafka or library type: notification system , kafka system (so in lld if we are implementing the kafka pub sub and channels so does the channel pushed to the consumer and notify them , or there is no notification to the subscriber, it is different from observer pattern i guess because in that the observer is notifed or is there a addtional queue at the observer end as well)
 * 7 does the inventory item is not observable or what like so here we can have push notfication but the place where the client has to read multiple then the client should have a queue to read from
 * 8 Player game: tic-tac-tow, chess, snake
 * 8 the rapido ride allocation don't get what it is similar to also is this can be asked in lld or only in hld
 * 9 recommendation systems like (food dish recommendation , youtube recoomendation , movierecommendation , tinder recommendation)
 * 10 project management tool like jira
 * 11 job scheduler
 * 12 ticketing system like jira
 * 15
 *
 * Inventory Management: Parking Lot, Movie Ticket, Amazon Item Store
 *
 * User Wallet Management: Stock Trading System (Broker only), Splitwise
 *
 * State Management: ATM Machine, Coffee Vendor, Elevator
 *
 * Filtering Query on Data: Logger, Database Design
 *
 * Pre-Math Logical: Rate Limiter, Splitwise Settlement, Trading Stock Allocation
 *
 * Kafka or Library Type: Notification System, Kafka System (Pub/Sub with consumer channels/notifications)
 *
 * Player Game: Tic-Tac-Toe, Chess, Snake
 *
 * Matching/Allocation: Rapido Ride Allocation
 *
 * Recommendation Systems: Food Dish, YouTube, Movie, Tinder
 *
 * Project Management Tool: Jira
 *
 * Job Scheduler
 *
 * Ticketing System: Jira (Duplicate of #10)
 * (Note: Your list skipped 13 & 14 and ended at an empty 15).
 *
 *
 * Now please write the machine coding interviews.interview code for ATM machine as per required in 1hr practical interivew actual (45-50min) indian inteview single java file with 1 functional, 2 non functional, 3 assumption, 4 hld callouts, 5 mention anything that we are not doing for runnable priority and time constraints and code all in single java file.
 * Practical means the comments limit is any big no problem.
 * code limit is 130-190 lines
 *
 */


/***
 * 1 parkingLot:
 * functional requirements:
 * - user should able to entry the vehicle
 * - user should able to exit the vehicle
 * - user should able to choose the payment method like cash , UPI, card;
 * - user should able to check the available slot for the vehicle type that it has
 * - the system should support multiple vehical type
 * non functional requirements:
 * - the slot accuiring must be consistent not two vehicle should we alloted same slot
 *
 * assumption: we are not handling for multiple floor or multiple zones. the slot addition is not handled here in this code
 *
 * entites:
 *  - class Slot{slotId, isoccupied, calculateHourlyCost(), markOccupied(), markFree()}
 *  - class twowheelerslot extends parkingslot {calculateHourlyCost()}
 *  - enum vehicleType{TWO_WHEELER, FOUR_WHEELER}
 *  - class vehicle {vehicleId, vehicleType}
 *  - class Ticket{ticketId, slot, vehicle, entrytime, exittime, calculateFair(){slot.calculateHourlyFair(exittime-entrytime);}}
 *  - interface PaymentStrategy{makePayment()}
 *  - class cash implements paymentStrategy{makePayment()}
 *  - class ParkingLotManager(){
 *      Map<slotId,slots> allSlot;
 *      Map<ticketId, Ticket> allTickets;
 *      List<Slot> getSlotForVehicle(vehicleType){
 *          iterate the allSlots and check the empty slots for this vehicle Type
 *      }
 *      makeEntry(vehicleId, slotId){
 *      check if slot is not already taken
 *      lockonslotid either by different rentrantLock per slot or by synchronized on that slot object only
 *      create a ticket
 *      mark the entry time
 *      realease lock
 *      finally {release lock}
 *      }
 *      makeexit(ticketId){
 *      getTicket
 *      getslot
 *      getvehicle
 *      calculateFair = Ticket.calculateFair()
 *      CashPaymentStrategy.makePayment(calculateFair,string paymentdetails){}
 *      onPaymensuccess release the slot
 *      }
 * }
 */

/***
 * 2 movieTicket booking system
 *
 * functional requirements
 * - user should able to search by movie name
 * - user should able to book multiple seat for a movie
 * - user should able to view the booking made is inprogress or done
 *
 * non functional requirements
 * - the seat accuring should be consistent
 * - get seat method should also be consistent or say eventually consistent
 *
 * assumption: the movie added are done by admin not through this code, and seat other data additon is also already assumed
 *
 * entities:
 * - Show{showId, movieId, list<ShowSeat>, starttime, endTime}
 * - Movie{movieId, movieName}
 * - ShowSeat{seatId, showId, isOccupied}
 * - TicketStatus{IN_PROGRESS, SUCCESS, FAILED}
 * - Ticket{ticketId, List<ShowSeat>, amount, ticketStatus}
 * - MovieBookingManager{
 *     Map<showid, Show> allShow;
 *     Map<movieName, List<Show>> allShowIndexByMovieName // just for searching purpose
 *     Map<ticketId,Ticket> allTicket// ledger of tickets
 *     Map<showSeatId, ReentarantLock> reentarantLock per showseatId
 *
 *     list<Show> getListOfShowByMovie(movieName)
 *     list<ShowSeat> getAvailableShowSeats(showId)
 *     Ticket checkoutSeat(ticketId, list<showSeatId>){
 *         check if the same ticketId already exist in the allTicket if already exist then duplicate call
 *         accuire reentarntLock for all showseats
 *         check if all the seats are free
 *         update all the seats
 *         create pending ticket
 *         finaly{free all reentarntlock accuired here}
 *     }
 *
 *     Ticket handlepayMentSuccess(ticketId){
 *      use synchornized for the ticket object
 *         change the ticket status
 *     }
 *
 *     Ticket handlepayMentFailure(ticketId){
 *  *      use synchornized for the ticket object
 *  *         change the ticket status
 *              take the reentarant locks for all the showseats
 *              update the status to free
 *
 *            finally(release all locks)
 *  *     }
 * }
 */

/***
 * ATM machine
 *
 * functional requirements
 * - user should be able to insert card and start session
 * - user should be able to check the balance
 * - user should be able to withdraw the cash
 *
 * nonfunctiona requirements
 * - the money dedcuted from the user bank accoutn and the money given as cash to user is consistent
 *
 *
 * assumption: the allocation strategy for the money to user is top to bottom first the biggest not is assigned only
 *
 * - CashVault{
 *     Map<Integer, Integer> currencyNoteCount;
 *     Map<Integer, Integer> heldNoteCount;
 *     heldNotes(Map<INteger,Integer> notesToHeld){
 *
 *     }
 *
 *     commitHeldNotes(){
 *         heldNoteCount = null;
 *     }
 *     rollBackHeldNotes(){
 *         // add the heldnotes in the currencyNotes
 *     }
 *     reserveAvaialbleNotesForMoney(int money){
 *         // using the strategy to check the higest notes first and taking the max amount of higest notes example amount is 1850 then take available 500 notes then take avaialble 100 notes then 50 then 20 then 10 if all the money can be made then only return true
 *         heldNOtes()
 *     }
 *
 * }
 *
 * - session{userId, userCardDetails, startTime}{
 *     boolean isActive()
 * }
 *
 * - interface BankSystem(){
 *      checkPin(cardDetails, pin)
 *     checkBalance(cardDetails)
 *     deductBalance(cardDetails)
 * }
 *
 * class SBISystem implements BankSystem
 *
 * ATMMachine{
 *     Session currentSession
 *     CashVault cashVault
 *     BankSystem
 *
 *     startSesstion(cardDetails,pin){
 *         bankSystem.checkPin()
 *         startSession()
 *     }
 *
 *     checkBalance(){
 *         if current session is active
 *         banksystem.checkbalance()
 *     }
 *
 *     withdrawMoney(int money){
 *         cashvault.reserveAvaialbleNotesForMoney()
 *         bankSystem.deductBalance()
 *         catch{
 *             cashvault.rollback()
 *         }
 *     }
 *
 *     callbackfrombank(){
 *     // if call back is success commit the held notes
 *     // if call back is fail the rollbank
 * }
 */


/***
 * Tic tac Toe
 * - functional requirements
 * - system should support intializing empty board
 * - system should support two players to play the game
 * - first person can choose any symbol the next person can choose from the any remaining symbol
 * - when any players wins the game should end and print the winning player
 *
 * entities:
 *
 * enum Symbol{CROSS, DASH, ZERO}
 * Player{id, name, Symbol}
 * Slot{slotNumber, Symbol, isOccupied}
 * Record Position{int x , int y}
 * Board{
 *  Map<Positions, Slot> slotMap
 *  Player1
 *  Player2
 *  int turn=0;
 *  CurrentPlayer
 *
 *  checkIfWinningConditionMet()
 *  checkIfMoveIdValid(player, position){
 *      chec if current player is the parameter player
 *      check if the position is empty
 *  }
 *  makeMove(){
 *      get slot from the board
 *      slot.isOccupied = true;
 *      slot.symbol = currentPlayer.symbol
 *      turn++;
 *      if turn is even currentPlayer = player1
 *      if turn is odd currentPlayer = player2
 *  }
 * }
 */


public class AllLLDPseudo {
}
