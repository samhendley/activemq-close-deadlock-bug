
package org.activemq.bug;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.Test;

import javax.jms.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.*;

public class DeadlockDuringCloseTest {


    @Test
    public void testCloseWhenHunk() throws Exception {

        ActiveMQConnectionFactory connectionFactory1 = new ActiveMQConnectionFactory();
        connectionFactory1.setBrokerURL("vm://localhost?brokerConfig=xbean:embedded-activemq-config.xml");
        connectionFactory1.setUseAsyncSend(true);
        connectionFactory1.setWatchTopicAdvisories(false);
        connectionFactory1.setOptimizeAcknowledge(true);
        connectionFactory1.setAlwaysSessionAsync(false);

        // start up the embedded broker which is running TCP on non-standard port
        connectionFactory1.createConnection().start();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL("tcp://localhost:61666");

        // TINY QUEUE is flow controlled after 1024 bytes
        final ActiveMQDestination destination = ActiveMQDestination.createDestination("queue://TINY_QUEUE", (byte) 0xff);

        Connection connection = connectionFactory.createConnection();
        connection.start();
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageProducer producer = session.createProducer(destination);
        producer.setTimeToLive(0);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        final AtomicReference<Exception> publishException = new AtomicReference<Exception>(null);
        final AtomicReference<Exception> closeException = new AtomicReference<Exception>(null);
        final AtomicLong lastLoop = new AtomicLong(System.currentTimeMillis() + 100);

        Thread pubThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] data = new byte[1000];
                    new Random(0xdeadbeef).nextBytes(data);
                    for (int i = 0; i < 10000; i++) {
                        lastLoop.set(System.currentTimeMillis());
                        ObjectMessage objMsg = session.createObjectMessage();
                        objMsg.setObject(data);
                        producer.send(destination, objMsg);
                    }
                } catch (Exception e) {
                    publishException.set(e);
                }
            }
        }, "PublishingThread");
        pubThread.start();

        // wait for publisher to deadlock
        while (System.currentTimeMillis() - lastLoop.get() < 1000) {
            Thread.sleep(100);
        }
        System.out.println("Publisher deadlock detected.");

        Thread closeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Attempting close..");
                    producer.close();
                } catch (Exception e) {
                    closeException.set(e);
                }
            }
        }, "ClosingThread");
        closeThread.start();

        try {
            closeThread.join(10000);
        } catch (InterruptedException ie) {
            assertFalse("Closing thread didn't complete in 10 seconds", true);
        }

        try {
            pubThread.join(10000);
        } catch (InterruptedException ie) {
            assertFalse("Publishing thread didn't complete in 10 seconds", true);
        }

        assertNull(closeException.get());
        assertNotNull(publishException.get());
    }
}
