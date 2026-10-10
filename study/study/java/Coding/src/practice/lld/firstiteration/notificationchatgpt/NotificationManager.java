package practice.lld.firstiteration.notificationchatgpt;

import java.util.concurrent.*;

enum NotificationType {
    MESSAGE,
    OTP
}

class Notification {

    int id;
    String message;

    int senderId;
    int receiverId;

    String receiverEmail;
    String receiverMobile;

    NotificationType notificationType;

    private static int idCounter = 1;

    public Notification(
            String message,
            int senderId,
            int receiverId,
            String receiverEmail,
            String receiverMobile,
            NotificationType notificationType
    ) {
        this.id = idCounter++;
        this.message = message;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.receiverEmail = receiverEmail;
        this.receiverMobile = receiverMobile;
        this.notificationType = notificationType;
    }
}

interface Channel {
    void send(Notification notification);
}

class EmailChannel implements Channel {

    private final BlockingQueue<Notification> queue;
    private final ExecutorService executor;

    public EmailChannel() {

        queue = new LinkedBlockingQueue<>();

        executor =
                Executors.newSingleThreadExecutor();

        startWorker();
    }

    private void startWorker() {

        executor.submit(() -> {

            while (
                    !Thread.currentThread()
                            .isInterrupted()
            ) {

                try {

                    Notification notification =
                            queue.take();

                    System.out.println(
                            "[EMAIL] NotificationId="
                                    + notification.id
                                    + " Email="
                                    + notification.receiverEmail
                                    + " Message="
                                    + notification.message
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread()
                            .interrupt();
                }
            }
        });
    }

    @Override
    public void send(
            Notification notification
    ) {
        queue.offer(notification);
    }
}

class SmsChannel implements Channel {

    private final BlockingQueue<Notification> queue;
    private final ExecutorService executor;

    public SmsChannel() {

        queue = new LinkedBlockingQueue<>();

        executor =
                Executors.newSingleThreadExecutor();

        startWorker();
    }

    private void startWorker() {

        executor.submit(() -> {

            while (
                    !Thread.currentThread()
                            .isInterrupted()
            ) {

                try {

                    Notification notification =
                            queue.take();

                    System.out.println(
                            "[SMS] NotificationId="
                                    + notification.id
                                    + " Mobile="
                                    + notification.receiverMobile
                                    + " Message="
                                    + notification.message
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread()
                            .interrupt();
                }
            }
        });
    }

    @Override
    public void send(
            Notification notification
    ) {
        queue.offer(notification);
    }
}

public class NotificationManager {

    private final Channel emailChannel;
    private final Channel smsChannel;

    public NotificationManager() {

        emailChannel = new EmailChannel();
        smsChannel = new SmsChannel();
    }

    public int sendSmsOnly(
            String message,
            int senderId,
            int receiverId,
            String mobile,
            NotificationType type
    ) {

        Notification notification =
                new Notification(
                        message,
                        senderId,
                        receiverId,
                        null,
                        mobile,
                        type
                );

        smsChannel.send(notification);

        return notification.id;
    }

    public int sendEmailOnly(
            String message,
            int senderId,
            int receiverId,
            String email,
            NotificationType type
    ) {

        Notification notification =
                new Notification(
                        message,
                        senderId,
                        receiverId,
                        email,
                        null,
                        type
                );

        emailChannel.send(notification);

        return notification.id;
    }

    public int sendEmailAndSms(
            String message,
            int senderId,
            int receiverId,
            String email,
            String mobile,
            NotificationType type
    ) {

        Notification notification =
                new Notification(
                        message,
                        senderId,
                        receiverId,
                        email,
                        mobile,
                        type
                );

        emailChannel.send(notification);
        smsChannel.send(notification);

        return notification.id;
    }

    public static void main(
            String[] args
    ) throws Exception {

        NotificationManager manager =
                new NotificationManager();

        int id1 =
                manager.sendSmsOnly(
                        "OTP is 1234",
                        1,
                        2,
                        "9999999999",
                        NotificationType.OTP
                );

        int id2 =
                manager.sendEmailOnly(
                        "Welcome User",
                        1,
                        2,
                        "abc@test.com",
                        NotificationType.MESSAGE
                );

        int id3 =
                manager.sendEmailAndSms(
                        "Payment Successful",
                        1,
                        2,
                        "abc@test.com",
                        "9999999999",
                        NotificationType.MESSAGE
                );

        System.out.println(
                "Generated Notification Ids: "
                        + id1 + ", "
                        + id2 + ", "
                        + id3
        );

        Thread.sleep(1000);
    }
}