package practice.lld.thirditeration.taskscheduler;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.*;

/**
 * functional requirements
 * - the admin should able to create jobs that can be picked parallely
 * - the jobs are completed in async manner time is provided in the job
 * - there is priority of the job that it should be picked first
 * - if the priority is same them pick the job which comes first
 * - we have to track the status of the inqueuejobs, schedulesjobs currently running , completed jobs, failed jobs
 * - at most 4 jobs can be scheduled at once
 *
 * non functional requirements
 * - the jobs should be executed multiple times
 * - the jobs should not be missed when inserting into the queue
 */

class Task{
    String taskId;
    int priority;
    int timeforcompletionmilliseconds;
    long arrivalepoch;
    public Task(String taskId, int priority, int Timeforcompletionmilliseconds, long arrivalepoch){
        this.taskId = taskId;
        this.priority = priority;
        this.timeforcompletionmilliseconds = Timeforcompletionmilliseconds;
        this.arrivalepoch = arrivalepoch;
    }
}

public class TaskScheduler {

    private ConcurrentHashMap<String, Task> nonScheduledTasks = new ConcurrentHashMap<String, Task>();
    private ConcurrentHashMap<String, Task> scheduledTasks = new ConcurrentHashMap<String, Task>();
    private ConcurrentHashMap<String, Task> completeTasks = new ConcurrentHashMap<String, Task>();
    private PriorityBlockingQueue<Task> nonScheduleQueue = new PriorityBlockingQueue<Task>(11,(Task a,Task b) ->{
        if(a.priority != b.priority){return Integer.compare(a.priority,b.priority);}
        return Long.compare(a.arrivalepoch,b.arrivalepoch);
    });
    ExecutorService taskSchedulerService;
    ExecutorService taskProcessingService;

    public TaskScheduler(int councurrentProcessingThreads){
        taskSchedulerService = Executors.newFixedThreadPool(2);
        taskProcessingService = Executors.newFixedThreadPool(councurrentProcessingThreads);
    }

    public Task acceptTaks(Task task){
        if(nonScheduledTasks.containsKey(task.taskId) || scheduledTasks.containsKey(task.taskId) ||  completeTasks.containsKey(task.taskId)){
            throw new IllegalArgumentException("Task already exists");
        }
        addTask(task);
        return task;
    }


    private void start(){
        taskSchedulerService.submit(()->{
            while(true){
                if(nonScheduleQueue.isEmpty()){
                    Thread.sleep(2000);
                }
                Task task = pullTask();// as we already have added this thread sleep for empty queue we can safely use the queue.poll as well insted of take
                taskProcessingService.submit(()->{
                    int timeToProcess = task.timeforcompletionmilliseconds;
                    try {
                        Thread.sleep(timeToProcess);
                    } catch (InterruptedException e) {
                        failTask(task);
                        throw new RuntimeException(e);// generally do not throw in the runnable it works in lamgda and close the thread but you have interrupt the current thread in correct way
                    }
                    completeTask(task);
                });
            }
        });
    }

    private synchronized void failTask(Task task){
        System.out.println(" the taskId: "+task.taskId+" is failed");
        scheduledTasks.remove(task.taskId); // even if we do putIfAbsent this is shit logic as this is fixed that only single thread can execute this it is ok, but simultaneously if other task is executing another part of code it might find that task is removed from the one map and hasn't been added to another map so this logical level is wrong you should think of different logical level only
        // better to take lock on the taskId as all threads across the service will not execute any of the multiple functions
        completeTasks.put(task.taskId, task);
    }

    private synchronized void completeTask(Task task){
        System.out.println(" the taskId: "+task.taskId+" is completed");
        scheduledTasks.remove(task.taskId);
        completeTasks.put(task.taskId, task);
    }

    private synchronized Task pullTask() throws InterruptedException {
        Task task = nonScheduleQueue.take(); // Note What is different between the take and the poll and does this poll return null if empty
        nonScheduledTasks.remove(task.taskId);
        scheduledTasks.put(task.taskId, task);
        return task;
    }

    private synchronized void addTask(Task task){
        nonScheduledTasks.put(task.taskId,task);
        nonScheduleQueue.offer(task); // Note What is differnece between the add and the offer
    }
}



























