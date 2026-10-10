package practice.lld.thirditeration.taskscheduler;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

enum TaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

class Task1 {

    final String taskId;
    final int priority;
    final int timeForCompletionMilliseconds;
    final long arrivalSequence;

    volatile TaskStatus status;

    public Task1(
            String taskId,
            int priority,
            int timeForCompletionMilliseconds,
            long arrivalSequence
    ) {
        this.taskId = taskId;
        this.priority = priority;
        this.timeForCompletionMilliseconds =
                timeForCompletionMilliseconds;
        this.arrivalSequence = arrivalSequence;
        this.status = TaskStatus.QUEUED;
    }
}

public class TaskSchedulerChatGpt {

    private final ConcurrentHashMap<String, Task1> tasks =
            new ConcurrentHashMap<>();

    private final PriorityBlockingQueue<Task1> queue =
            new PriorityBlockingQueue<>(
                    11,
                    (a, b) -> {

                        int priorityComparison =
                                Integer.compare(
                                        a.priority,
                                        b.priority
                                );

                        if (priorityComparison != 0) {
                            return priorityComparison;
                        }

                        return Long.compare(
                                a.arrivalSequence,
                                b.arrivalSequence
                        );
                    }
            );

    private final ExecutorService workerExecutor;

    private final AtomicLong sequence =
            new AtomicLong(0);

    private volatile boolean running = true;

    public TaskSchedulerChatGpt(int numberOfWorkers) {

        workerExecutor =
                Executors.newFixedThreadPool(numberOfWorkers);
    }

    public void start() {

        int numberOfWorkers =
                ((ThreadPoolExecutor) workerExecutor)
                        .getCorePoolSize();

        for (int i = 0; i < numberOfWorkers; i++) {

            workerExecutor.submit(this::workerLoop);
        }
    }

    private void workerLoop() {

        while (running && !Thread.currentThread().isInterrupted()) {

            try {

                // Blocks until task is available
                Task1 task = queue.take();

                task.status = TaskStatus.RUNNING;

                processTask(task);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public Task1 submit(
            String taskId,
            int priority,
            int processingTimeMs
    ) {

        Task1 task = new Task1(
                taskId,
                priority,
                processingTimeMs,
                sequence.getAndIncrement()
        );

        // Atomic duplicate check + insert
        if (tasks.putIfAbsent(taskId, task) != null) {
            throw new IllegalArgumentException(
                    "Task1 already exists: " + taskId
            );
        }

        queue.offer(task);

        return task;
    }

    private void processTask(Task1 task) {

        try {

            System.out.println(
                    Thread.currentThread().getName()
                            + " started "
                            + task.taskId
            );

            Thread.sleep(
                    task.timeForCompletionMilliseconds
            );

            task.status = TaskStatus.COMPLETED;

            System.out.println(
                    Thread.currentThread().getName()
                            + " completed "
                            + task.taskId
            );

        } catch (InterruptedException e) {

            task.status = TaskStatus.FAILED;

            Thread.currentThread().interrupt();
        }
    }

    public Task1 getTask(String taskId) {
        return tasks.get(taskId);
    }

    public void shutdown() {

        running = false;

        workerExecutor.shutdownNow();
    }
}