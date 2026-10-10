package practice.lld.thirditeration.notificationservicekafka.iteration1;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * functional requirement
 * - user
 */

class Message{
    String messageId;
    String messageText;
    public Message(String messageId, String messageText){this.messageId=messageId;this.messageText=messageText;}
}

interface Consumer{
    public void start(CopyOnWriteArrayList<Message> messages);
}
class ConsumerAnalytics implements Consumer{
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private AtomicInteger offsetToReadFromArray = new AtomicInteger(0);
    public void start(CopyOnWriteArrayList<Message> messages){

        executor.submit(()->{
            while(true){
                int currentOffset = offsetToReadFromArray.getAndIncrement();
                if(currentOffset<messages.size()){
                    System.out.println("reading or executing mesage "+ messages.get(currentOffset) + " message text is here : "+messages.get(currentOffset).messageText);
                }else{
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }
}

class Topic{
    private final CopyOnWriteArrayList<Message> messages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer> consumers = new CopyOnWriteArrayList<>();

    public Topic(){}

    public void subscribe(Consumer consumer){
        consumers.add(consumer);
        consumer.start(messages);
    }

    public void addMessage(Message message){
        messages.add(message);
    }
}

public class NotificationServiceKafka {

    public static void main(String[] args) throws InterruptedException {

        Topic topic1 = new Topic();

        Consumer consumer1 = new ConsumerAnalytics();

        Message message = new Message("messageid1", "ooollll");

        topic1.subscribe(consumer1);

        topic1.addMessage(message);
    }
}


