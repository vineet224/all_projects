package practice.lld.thirditeration.elevator;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

enum ElevatorState{RUNNING,IDLE}
enum ElevatorDirection{UP,DOWN}

class Elevator{

    String elevatorId;
    TreeSet<Integer> floorsToVisit;
    ElevatorState elevatorState;
    ElevatorDirection  elevatorDirection;
    int currentFloor;

    public Elevator(String elevatorId){
        this.elevatorId = elevatorId;
        floorsToVisit = new TreeSet<>();
        elevatorState = ElevatorState.IDLE;
        currentFloor = 0;
        elevatorDirection = ElevatorDirection.UP;
    }

    public synchronized void addFloor(int floor){
        floorsToVisit.add(floor);
    }

    public synchronized void move(){
        elevatorState =  ElevatorState.RUNNING;
        Integer floorUp=null, floorDown=null,newFloor=null;
        floorUp = floorsToVisit.higher(currentFloor);
        floorDown = floorsToVisit.lower(currentFloor);

        if(elevatorDirection == ElevatorDirection.UP && floorUp != null){
            newFloor = floorUp;
        }
        else if(elevatorDirection ==  ElevatorDirection.DOWN && floorDown != null){
            newFloor = floorDown;
        }
        else if(elevatorDirection == ElevatorDirection.UP){
            newFloor = floorDown;
            elevatorDirection = ElevatorDirection.DOWN;
        }else{
            newFloor =  floorUp;
            elevatorDirection = ElevatorDirection.UP;
        }

        if(newFloor != null){
            currentFloor = newFloor;
            floorsToVisit.remove(newFloor);
            System.out.println("visisted the floor "+newFloor + " elevator id "+elevatorId);
            elevatorState = ElevatorState.RUNNING;
        }else{
            elevatorState =  ElevatorState.IDLE;
        }
    }
}

public class ElevatorManager {

    HashMap<String,Elevator> elevators;
    ExecutorService executorService;
    public ElevatorManager(HashMap<String,Elevator> elevators){
        this.elevators = elevators;
        executorService = Executors.newFixedThreadPool(elevators.size());
    }

    public void addFloor(int floor, String elevatorId){
        Elevator elevator = elevators.get(elevatorId);
        elevator.addFloor(floor);
    }

    public void startElevators(){
        for(Elevator elevator:elevators.values()) {
            executorService.submit(() -> {
                while (true) {
                    if (elevator.floorsToVisit.isEmpty()) {
                        Thread.sleep(200);
                    }
                    elevator.move();
                    Thread.sleep(100);
                }
            });
        }
    }


    public static void main(String[] args){

        Elevator elevator1 = new Elevator("elevator1");
        Elevator elevator2 = new Elevator("elevator2");
        HashMap<String, Elevator> elevataors = new HashMap<String, Elevator>();
        elevataors.put("elevator1",elevator1);
        elevataors.put("elevator2",elevator2);


        ElevatorManager manager = new ElevatorManager(elevataors);
        manager.addFloor(1,"elevator1");
        manager.addFloor(2,"elevator2");

        manager.startElevators();
    }
}
