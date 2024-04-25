//package com.hmdp;
//
//import com.rabbitmq.client.Channel;
//import com.rabbitmq.client.Connection;
//import com.rabbitmq.client.ConnectionFactory;
//
//import java.io.IOException;
//import java.util.stream.IntStream;
//
///**
// * @author Wwh
// * @ProjectName hm-dianping
// * @dateTime 2024/4/24 下午3:24
// * @description RabbitMQTest
// **/
//public class RabbitMQTest {
//
//    private final static String QUEUE_NAME = "First";
//
//    private  static Connection connection;
//    static {
//        ConnectionFactory factory = new ConnectionFactory();
//        factory.setHost("47.93.83.136");
//        factory.setPort(5672);
//        factory.setUsername("Wwhds");
//        factory.setPassword("WwhdsOne");
//        try {
//            connection = factory.newConnection();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public static void send() throws IOException {
//        //从连接中创建通道
//        Channel channel = connection.createChannel();
//        //声明队列
//        /*
//            QUEUE_NAME：这是队列的名称，类型为 String。
//            durable：这是一个布尔值，如果设置为 true，则队列将在服务器重启后仍然存在，如果设置为 false，则队列在服务器重启后将被删除。
//            exclusive：这是一个布尔值，如果设置为 true，则队列只能被声明它的连接（Connection）访问，并且在该连接关闭时队列会被删除。如果设置为 false，则队列可以被其他连接访问。
//            autoDelete：这是一个布尔值，如果设置为 true，则队列在最后一个消费者断开连接后会自动被删除。如果设置为 false，则队列在最后一个消费者断开连接后不会被删除。
//            arguments：这是一个 Map<String, Object> 类型的参数，用于设置队列的其他属性，例如消息的生存时间（TTL）、队列的最大长度等。如果不需要设置这些属性，可以传入 null。
//         */
//        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
//
//        IntStream.range(1, 10000).forEach(i -> { // 发送1000条消息
//            String message = "Hello World! Message number: " + i;
//            try {
//                channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//        channel.close();
//    }
//
//    private static final Long SleepTime = 1000L;
//
//    public static void receiveOne() throws IOException {
//        //从连接中创建通道
//        Channel channel = connection.createChannel();
//        //声明队列
//        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
//        //声明消费者
//        QueueingConsumer consumer = new QueueingConsumer(channel);
//        //设置通道消费者
//        //手动确认消息
//        channel.basicQos(100); // 一次只接受100条消息
//        channel.basicConsume(QUEUE_NAME, false, consumer);
//        while(true){
//            try {
//                //获取消息
//                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
//                String message = new String(delivery.getBody());
//                System.out.println("队列1" + " 收到消息，内容为：" + message);
//                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
//                Thread.sleep(SleepTime/100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    public static void receiveTwo() throws IOException {
//        //从连接中创建通道
//        Channel channel = connection.createChannel();
//        //声明队列
//        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
//        //声明消费者
//        QueueingConsumer consumer = new QueueingConsumer(channel);
//        //设置通道消费者
//        //手动确认消息
//        channel.basicQos(1); // 一次只接受一条消息
//        channel.basicConsume(QUEUE_NAME, false, consumer);
//        while(true){
//            try {
//                //获取消息
//                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
//                String message = new String(delivery.getBody());
//                System.out.println("队列2" + " 收到消息，内容为：" + message);
//                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
//                Thread.sleep(SleepTime);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//
//}
