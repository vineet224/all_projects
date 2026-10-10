package practice.lld.firstiteration.elevator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/***
 * functional
 * - user can click on a button at the floor to go up or down
 * - user inside the elevator can choose the floor
 *
 * non functional requirements
 * - the elevators should spend less resources moving like if only one is sufficient
 *
 * Entities
 * - Elevator
 * - ElevatorSelectionStrategy
 * - moveRequest
 * - ElevatorState
 */

enum ElevatorState{
    IDLE,
    MOVING
}

enum ElevatorDirection{
    DOWN,
    UP
}

class Elevator{
    int id;
    int currentFloor;
    ElevatorState state;
    ElevatorDirection direction;


    Queue<Integer> floorToVisit;

    static int idCount = 0;

    public Elevator(){
        id = idCount;
        idCount++;
        currentFloor =0;
        state = ElevatorState.IDLE;
        floorToVisit = new LinkedList<>();
        direction = ElevatorDirection.UP;
    }

    public void internalMoveRequest(int floor){
        // check if it is not already present in the queue no sense to add it again
        floorToVisit.add(floor); // currently it is first come first serve so yeah
    }

    public int move() throws InterruptedException {

        if(floorToVisit.isEmpty()){
            System.out.println(" the elevator do not have any job");
            return currentFloor;
        }

        int newfloor = floorToVisit.poll();
        if(newfloor > currentFloor) direction = ElevatorDirection.UP;
        else direction = ElevatorDirection.DOWN;
        //Thread.currentThread().sleep(5000); // dont know how this will work as there are two instances of the elevator
        currentFloor = newfloor;
        System.out.println(" the elevator is moved to the currentfloor"+ currentFloor);
        return currentFloor;
    }

}

class ElevatorSelector{

    public int getElevatorToAssignRequest(int requestedFloor, ArrayList<Elevator> elevators){
        // so just check which ever elevators current floor is near to this given floor
        // if both are moving to the direction of the floor select the min dis elevator with this floor
        // if only one is moving toward and other is moving away choose the moving toward one
        // if both moving away choose the away one

        Elevator elevator1 = elevators.get(0);
        ElevatorDirection direction1 = elevator1.direction;
        int floor1 = elevator1.currentFloor;

        for(int i=1;i<elevators.size();i++){
            Elevator elevator2 = elevators.get(i);

            ElevatorDirection direction2 = elevator2.direction;
            int floor2 = elevator2.currentFloor;

            int dis1 = requestedFloor - floor1;
            int dis2 = requestedFloor - floor2;

            if(Math.abs(dis2)< Math.abs(dis1)){
                elevator1 = elevator2;
            }
        }

        return elevator1.id;
    }

}

public class ElevatorManager {

    HashMap<Integer, Elevator> elevatorsMap;
    ArrayList<Elevator> elevators;
    ElevatorSelector elevatorSelector;

    public ElevatorManager(HashMap<Integer, Elevator> elevatorsMap, ArrayList<Elevator> elevators, ElevatorSelector elevatorSelector) throws InterruptedException {
        this.elevatorsMap = elevatorsMap;
        this.elevators = elevators;
        this.elevatorSelector = elevatorSelector;
        startElevatorsInSeperateThread();
    }


    public int requestElevatorOnFloor(int floor){
        int elevatorId = elevatorSelector.getElevatorToAssignRequest(floor,elevators);
        Elevator elevator = elevatorsMap.get(elevatorId);
        elevator.internalMoveRequest(floor);

        return elevatorId;
    }

    public void moveElevatorTo(int floor, int elevatorId){
        Elevator elevator = elevatorsMap.get(elevatorId);
        elevator.internalMoveRequest(floor);
        return;
    }

    public void startElevatorsInSeperateThread() throws InterruptedException {

        Thread thread1 = new Thread(()->{
            try {
                elevators.get(0).move();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        Thread thread2 = new Thread(()->{
            try {
                elevators.get(1).move();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        while(true){
            // new thread for elevator 1 and call the move of that elevator 1
            // new thread for elevator 2 and call the move of the elevator 2

            thread1.start();
            thread2.start();

            Thread.sleep(5000);
        }
    }

    // requestElevatorOnFloor(int floor)
    // moveElevatorTo(int elevatorId, int floor) // coming from inside the elevator
    // startElevator() basically start the elevator is the different threads after 5 seconds continously call the move function of the elevator if the elevator has the non empty queue


    public static void main(String[] args) throws InterruptedException {

        Elevator elevator1 = new Elevator();
        Elevator elevator2 = new Elevator();

        HashMap<Integer, Elevator> elevatorHashMap = new HashMap<>();
        elevatorHashMap.put(elevator1.id, elevator1);
        elevatorHashMap.put(elevator2.id, elevator2);

        ArrayList<Elevator> arrayList = new ArrayList<>();
        arrayList.add(elevator1);
        arrayList.add(elevator2);

        ElevatorSelector elevatorSelector = new ElevatorSelector();

        ElevatorManager elevatorManager = new ElevatorManager(elevatorHashMap, arrayList, elevatorSelector);

        elevatorManager.moveElevatorTo(1, elevator1.id);

    }

}
