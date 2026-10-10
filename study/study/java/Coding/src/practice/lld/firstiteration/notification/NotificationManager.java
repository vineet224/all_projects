package practice.lld.firstiteration.notification;


import java.util.concurrent.*;

/***
 * funtional requirements
 * - user can send notification with multiple channels selecting any number of channels
 * - user should receive a message id that the message is send
 *
 * entity
 * Notification {id, message, channel:[], }
 * Channel
 *
 */

enum NotificationType{
    Message,
    OTP
}



class Notification{
    int id;
    String message;
    int senderId;
    int receiverId;
    String receiverEmail;
    String receiverMobile;
    NotificationType notificationType;

    // ArrayList<Channel> channelsToSend // should be have  the notification to have the channel where to send or the notificaitonType only determines which allchannels to use

    static int idCount = 0;

    public Notification(String message, int senderId, int receiverId, String receiverEmail, String receiverMobile, NotificationType notificationType){
        this.id = idCount;
        idCount++;
        this.message = message;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.receiverEmail = receiverEmail;
        this.receiverMobile = receiverMobile;
        this.notificationType = notificationType;

    }

}

interface Channel{
    public Notification send(Notification notification);
}

class EmailChannel implements  Channel{

    BlockingDeque<Notification> notificationToDeliver;
    ExecutorService executorService;


    public EmailChannel(){
        notificationToDeliver = new LinkedBlockingDeque<>();
        this.executorService = Executors.newFixedThreadPool(2);
        start();
    }

    private void start(){
        executorService.submit(()->{
            while(true){
                Notification notification = notificationToDeliver.poll();
                System.out.println(" the notification is deliverd through email"+ notification.message + " using email :"+ notification.receiverEmail);
                Thread.sleep(1000);
            }
        });
    }


    @Override
    public Notification send(Notification notification) {
        notificationToDeliver.add(notification);
        return null;
    }
}

class SmsChannel implements Channel{

    BlockingDeque<Notification> notificationToDeliver;
    ExecutorService executorService;

    public SmsChannel(){
        notificationToDeliver = new LinkedBlockingDeque<>();
        this.executorService = Executors.newFixedThreadPool(2);
        start();
    }

    private void start(){
        executorService.submit(()->{
            while(true){
                Notification notification = notificationToDeliver.poll();
                System.out.println(" the notification is deliverd through sms"+ notification.message);
                Thread.sleep(1000);
            }
        });
    }

    @Override
    public Notification send(Notification notification) {
        notificationToDeliver.add(notification);
        return null;
    }
}

public class NotificationManager {

    Channel SmsChannel;
    Channel emailChannel;

    public NotificationManager(){
        this.emailChannel = new EmailChannel();
        this.SmsChannel = new SmsChannel();
    }

    public void sendSmsOnly(String message, int senderId, int receiverId, String moibleNo, NotificationType notificationType){
        Notification newNotificaiton = new Notification(message,senderId, receiverId, null, moibleNo, notificationType);
    }

    public void sendEmailOnly(String message, int senderId, int receiverId, String email, NotificationType notificationType){
        Notification newNotificaiton = new Notification(message,senderId, receiverId, email, null, notificationType);
    }

    public void sendEmailAndSms(String message, int senderId, int receiverId, String email, String mobileNO, NotificationType notificationType){
        Notification newNotificaiton = new Notification(message,senderId, receiverId, email, mobileNO, notificationType);
    }

}
