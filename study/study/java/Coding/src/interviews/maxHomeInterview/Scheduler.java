package interviews.maxHomeInterview;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


public class Scheduler {
    private final int maxConcurrent;
    private final PriorityBlockingQueue<Task> queue;
    private ExecutorService executorService;
    private boolean startedFlag = false; // volatile
    private final AtomicInteger sequence = new AtomicInteger(0);

    public Scheduler(int maxConcurrent){
        if(maxConcurrent<=0){
            throw new IllegalArgumentException("maxconcurrent should be be greater then zero");
        }
        this.maxConcurrent = maxConcurrent;
        this.queue = new PriorityBlockingQueue<Task>(11, (a,b) -> {
            if(a.priority != b.priority){
                return Integer.compare(a.priority, b.priority);
            }
            return Long.compare(a.sequence, b.sequence);
        });
    }

    public void addTask(int durationMs,String name, int priority){

        Task task= new Task(durationMs, name, priority, sequence.getAndIncrement());
        queue.offer(task);
    }

    private void workerLoop(){

        while(true){

            try{
                Task task = queue.take();

                LocalDateTime current = LocalDateTime.now();
                System.out.println("started" + task.name + " on : "+ current);

                Thread.sleep(task.durationMs);

                LocalDateTime finishedTime = LocalDateTime.now();

                System.out.println("Finished" + task.name+ " finished time "+ finishedTime);

            }catch(Exception e){
                break;
            }
        }
    }

    public void start(){

        if(startedFlag){
            throw new RuntimeException("Already started");
        }

        startedFlag = true;

        for(int i=0;i<maxConcurrent;i++){
            Thread worker = new Thread(this::workerLoop);
            worker.start();
        }
    }


    private static class Task {
        int durationMs;
        String name;
        int priority;
        long sequence;
        Task(int durationMs, String name, int priority, long sequence){
            this.durationMs = durationMs;
            this.name = name;
            this.priority = priority;
            this.sequence = sequence;
        }

//        @Override
//        public int compareTo(Task task2) {
//
//            if(this.priority != task2.priority){
//                return Integer.compare(this.priority, task2.priority);
//            }
//            return Long.compare(this.sequence, task2.sequence);
//        }
    }

    public static void main(String[] args) throws Exception{
        Scheduler scheduler = new Scheduler(2);

        scheduler.addTask(1000, "Task1", 1);
        scheduler.addTask(2000, "Task2",0);
        scheduler.addTask(1000, "Task3",2);
        scheduler.addTask(1000, "Task4",1);

        scheduler.start();
    }























}
