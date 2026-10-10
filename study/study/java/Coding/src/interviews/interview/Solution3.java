package interviews.interview;


import java.util.HashMap;
import java.util.TreeSet;

/***
 * funtional requirements
 * - add user can add task with priority
 * - modify the task, we for now only update the task priority
 * - remove a task , not that ttl one
 *
 *
 * core enties
 * Task
 * TaskManagerService - queue or set
 *
 * - TreeSet{priotiry, timestamp taskId} - highest priority it will Ologn
 * - processing in sequentional
 *
 *
 */

class Task{
    int taskId; // unique id
    int priority;
    long timeStamp =0;
    long ttl;
    String userId;

    // metadata we could add later

    static int taskidCounter = 0; // thread safe instead

    public Task(int priority, String userId, long ttl){
        this.priority = priority;
        this.userId = userId;
        this.taskId = taskidCounter;
        taskidCounter++;
        timeStamp = System.currentTimeMillis();
        this.ttl = ttl;
    }
}

class TaskMangerService{

    TreeSet<Task> tasksQueue; // user t hread safe

    HashMap<Integer, Task> taskMap ;


    public TaskMangerService(){
        this.tasksQueue = new TreeSet<Task>((a,b) -> {
            if(a.priority!=b.priority){
                return Integer.compare(a.priority,b.priority);
            }
            return Long.compare(a.timeStamp,b.timeStamp); // assuming all the timestamps are unique
        });
        this.taskMap = new HashMap<>();
    }

    public Task addTask(String userId, int priority, long ttl){
        // we could add validation here like duplicate task based meta or userId
        Task newTask = new Task(priority,userId, ttl);
        taskMap.put((newTask.taskId),newTask);
        tasksQueue.add(newTask);

        return newTask;
    }

    public Task removeTask(int taskId){


        if(!checkIfExist(taskId)){
            System.out.println("No task with taskId:" + taskId + " exists");
            return null;
        }

        Task task = taskMap.get(taskId);
        // here operations are on single taskid ,
        // lock as well for the taskId
        taskMap.remove(taskId);
        tasksQueue.remove(task);
        return task;
    }

    public Task updateTaskPriority(int taskId,int priority){

        // here operations are on single taskid ,
        // lock as well for the taskId
        if(!checkIfExist(taskId)){
            System.out.println("No task with taskId:" + taskId + " exists");
            return null;
        }

        Task task = taskMap.get(taskId);
        tasksQueue.remove(task);
        task.priority = priority;
        tasksQueue.add(task);

        return task;
    }

    public void executeHighesPriorityTask(){


        if(tasksQueue.size() == 0 ){
            System.out.println(" no task to execute");
            return;
        }


        Task task = tasksQueue.first(); //  that also removes
        // lock the task for updation priority for removal
        tasksQueue.remove(task);
        taskMap.remove(task.taskId);
        long currentTimeStamp = System.currentTimeMillis();
        if(task.timeStamp + task.ttl < currentTimeStamp){
            System.out.println(" the task is already expired");
            return;
        }
        System.out.println(" the task picked id: "+ task.taskId + " priority "+ task.priority);
        // lock expired , release
    }

    private boolean checkIfExist(int taskId){
        return taskMap.containsKey(taskId);
    }
}

public class Solution3 {

    public static void  main(String[] args){
        TaskMangerService taskMangerService = new TaskMangerService();

        long ttl = 4000;
        // we defin a local date time that after this it is not mean to execute
        Task task1 = taskMangerService.addTask("vineet", 1, ttl);
        Task task2 = taskMangerService.addTask("vineet2", 0, ttl );

        taskMangerService.executeHighesPriorityTask();
        taskMangerService.executeHighesPriorityTask();

        taskMangerService.removeTask(task1.taskId);
        taskMangerService.updateTaskPriority(task2.taskId, 5);

        taskMangerService.executeHighesPriorityTask();
        taskMangerService.executeHighesPriorityTask();

    }
}
