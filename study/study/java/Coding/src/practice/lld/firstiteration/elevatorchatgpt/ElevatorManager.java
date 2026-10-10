package practice.lld.firstiteration.elevatorchatgpt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

enum ElevatorState {
    IDLE,
    MOVING
}

enum ElevatorDirection {
    DOWN,
    UP
}

class Elevator implements Runnable {

    int id;
    int currentFloor;
    ElevatorState state;
    ElevatorDirection direction;

    Queue<Integer> floorToVisit;

    static int idCount = 0;

    public Elevator() {
        id = idCount++;
        currentFloor = 0;
        state = ElevatorState.IDLE;
        direction = ElevatorDirection.UP;

        floorToVisit = new ConcurrentLinkedQueue<>();
    }

    public void internalMoveRequest(int floor) {

        floorToVisit.add(floor);

        System.out.println(
                "Request added -> Elevator "
                        + id
                        + " Floor "
                        + floor
        );
    }

    public void move() throws InterruptedException {

        Integer targetFloor = floorToVisit.poll();

        if (targetFloor == null) {
            state = ElevatorState.IDLE;
            return;
        }

        state = ElevatorState.MOVING;

        if (targetFloor > currentFloor) {
            direction = ElevatorDirection.UP;
        } else {
            direction = ElevatorDirection.DOWN;
        }

        while (currentFloor != targetFloor) {

            if (direction == ElevatorDirection.UP) {
                currentFloor++;
            } else {
                currentFloor--;
            }

            System.out.println(
                    "Elevator "
                            + id
                            + " at floor "
                            + currentFloor
            );

            //Thread.sleep(1000);
        }

        System.out.println(
                "Elevator "
                        + id
                        + " reached floor "
                        + currentFloor
        );

        state = ElevatorState.IDLE;
    }

    @Override
    public void run() {

        while (true) {

            try {

                move();

                Thread.sleep(500);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

class ElevatorSelector {

    public int getElevatorToAssignRequest(
            int requestedFloor,
            ArrayList<Elevator> elevators
    ) {

        Elevator best = elevators.get(0);

        int bestDistance =
                Math.abs(
                        best.currentFloor
                                - requestedFloor
                );

        for (int i = 1; i < elevators.size(); i++) {

            Elevator cur = elevators.get(i);

            int curDistance =
                    Math.abs(
                            cur.currentFloor
                                    - requestedFloor
                    );

            if (curDistance < bestDistance) {

                best = cur;
                bestDistance = curDistance;
            }
        }

        return best.id;
    }
}

public class ElevatorManager {

    Map<Integer, Elevator> elevatorsMap;

    ArrayList<Elevator> elevators;

    ElevatorSelector elevatorSelector;

    public ElevatorManager(
            Map<Integer, Elevator> elevatorsMap,
            ArrayList<Elevator> elevators,
            ElevatorSelector elevatorSelector
    ) {

        this.elevatorsMap = elevatorsMap;
        this.elevators = elevators;
        this.elevatorSelector = elevatorSelector;

        startElevatorsInSeparateThreads();
    }

    public int requestElevatorOnFloor(
            int floor
    ) {

        int elevatorId =
                elevatorSelector
                        .getElevatorToAssignRequest(
                                floor,
                                elevators
                        );

        Elevator elevator =
                elevatorsMap.get(elevatorId);

        elevator.internalMoveRequest(floor);

        return elevatorId;
    }

    public void moveElevatorTo(
            int floor,
            int elevatorId
    ) {

        Elevator elevator =
                elevatorsMap.get(elevatorId);

        elevator.internalMoveRequest(floor);
    }

    public void startElevatorsInSeparateThreads() {

        for (Elevator elevator : elevators) {

            Thread thread =
                    new Thread(elevator);

            thread.start();
        }
    }

    public static void main(String[] args)
            throws Exception {

        Elevator elevator1 = new Elevator();
        Elevator elevator2 = new Elevator();

        Map<Integer, Elevator> elevatorMap =
                new ConcurrentHashMap<>();

        elevatorMap.put(
                elevator1.id,
                elevator1
        );

        elevatorMap.put(
                elevator2.id,
                elevator2
        );

        ArrayList<Elevator> elevators =
                new ArrayList<>();

        elevators.add(elevator1);
        elevators.add(elevator2);

        ElevatorSelector selector =
                new ElevatorSelector();

        ElevatorManager manager =
                new ElevatorManager(
                        elevatorMap,
                        elevators,
                        selector
                );

        Thread.sleep(2000);

        int e1 =
                manager.requestElevatorOnFloor(5);

        Thread.sleep(1000);

        manager.moveElevatorTo(10, e1);

        Thread.sleep(1000);

        int e2 =
                manager.requestElevatorOnFloor(2);

        Thread.sleep(1000);

        manager.moveElevatorTo(8, e2);
    }
}



//ExecutorService executor =
//        Executors.newFixedThreadPool(2);
//
//executor.submit(() -> {
//        while(true){
//        elevator1.move();
//        Thread.sleep(500);
//    }
//            });