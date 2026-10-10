package practice.lld.thirditeration.kafkalite;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * functional requirements
 * - In this lld we are dealing with a single topic
 * - This single topic can be subscribed by multiple consumers
 * - This system is kafka like consumers has to pull and maintain the offset not rabbitmq like where queue is pushing to consumers
 */
class Message{
    String messageId;
    String messageText;
    public  Message(String messageId, String messageText) {
        this.messageId = messageId;
        this.messageText = messageText;
    }
}

class Consumer{
    String consumerId;
    AtomicInteger offset;
    ExecutorService  executor;
    public Consumer(String consumerId){
        this.consumerId = consumerId;
        offset = new AtomicInteger(0);
        executor = Executors.newSingleThreadExecutor();
    }

    public void consumerMessage(Message message){
        // the consumer when consuming would check the idemptoency key using messageId
        System.out.println("Consumer Message Received and executed "+message.messageId);
    }

    public void start(CopyOnWriteArrayList<Message> messages){
        executor.submit(()->{
            while(true){
                int currentOffset =  offset.get();
                if(messages.size()>currentOffset){
                    Message message = messages.get(currentOffset);
                    consumerMessage(message);
                    offset.compareAndSet(currentOffset,currentOffset+1); // there is not need for this currently only single thread is executing this
                }else{
                    Thread.sleep(100);
                }
            }
        });
    }
}

class Topic{
    String topicId;
    CopyOnWriteArrayList<Message> messages = new CopyOnWriteArrayList<>();
    ConcurrentHashMap<String,Consumer> consumers = new ConcurrentHashMap<>();

    public Topic(String topicId){
        this.topicId = topicId;
    }

    public void addConsumer(Consumer consumer){
        if(consumers.putIfAbsent(consumer.consumerId,consumer) != null){
            System.out.println("Consumer Already Exists");
            return;
        }
        consumer.start(messages);
    }

    public void addMessage(Message message){
        messages.add(message);
    }

}

public class KafkaLite {

    public static void main(String[] args) throws InterruptedException {

        Topic topic = new Topic("topic");
        Consumer consumer = new Consumer("consumer1");
        Consumer consumer2 = new Consumer("consumer2");

        topic.addMessage(new Message("messageid1","text of message1"));
        topic.addMessage(new Message("messageid2","text of message2"));

        topic.addConsumer(consumer);

        topic.addMessage(new Message("messageid3","text of message3"));

        Thread.sleep(1000);
        topic.addConsumer(consumer2);

        topic.addMessage(new Message("messageid4","text of message4"));
    }
}
