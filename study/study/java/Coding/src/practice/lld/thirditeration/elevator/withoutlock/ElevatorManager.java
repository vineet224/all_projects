package practice.lld.thirditeration.elevator.withoutlock;

import java.util.ArrayList;
import java.util.TreeSet;

enum Direction{
    IDLE,
    UP,
    DOWN,
}

class FloorRequest{
    int floor;
    Direction direction;
    public FloorRequest(int floor, Direction direction){this.floor=floor;this.direction=direction;}
}

class Elevator{
    String elevatorId;
    int currentFloor;
    Direction direction;

    TreeSet<Integer> upFloors;
    TreeSet<Integer> downFloors;

    public Elevator(String elevatorId){
        this.elevatorId=elevatorId;
        this.currentFloor=0;
        this.direction=Direction.IDLE;
        this.upFloors=new TreeSet<>();
        this.downFloors=new TreeSet<>();
    }

    public void addFloor(int floor){
        if(floor == currentFloor) return;
        if(floor > currentFloor){
            upFloors.add(floor);
        }else{
            downFloors.add(floor);
        }

        if(direction==Direction.IDLE){
            direction = (floor > currentFloor) ? Direction.UP : Direction.DOWN;
        }
    }

    public void step(){
        if(direction==Direction.IDLE){return;}
        if(direction==Direction.UP){
            currentFloor=currentFloor+1;
            if(upFloors.contains(currentFloor)){
                upFloors.remove(currentFloor);
                System.out.println("reached this floor"+currentFloor+ " through elevator "+elevatorId);
            }
        }
        if(direction==Direction.DOWN){
            currentFloor=currentFloor-1;
            if(downFloors.contains(currentFloor)){
                downFloors.remove(currentFloor);
                System.out.println("reached this floor"+currentFloor+ " through elevator "+elevatorId);
            }
        }
        if(upFloors.isEmpty() &&  downFloors.isEmpty()){
            direction=Direction.IDLE;
        }
    }
}

class NearestCarStrategy{

    public Elevator selectElevator(ArrayList<Elevator> elevators,FloorRequest request){
        Elevator best = null;
        int minDis = Integer.MAX_VALUE;
        for(Elevator elevator:elevators){
            int dis = Math.abs(elevator.currentFloor -  request.floor);

            if(elevator.direction==Direction.IDLE || elevator.direction == request.direction){
                if(dis<minDis){
                    best=elevator;
                    minDis=dis;
                }
            }
        }
        return best;
    }
}

public class ElevatorManager {
}
