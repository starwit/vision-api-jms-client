package de.starwit;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import com.google.protobuf.InvalidProtocolBufferException;

import de.starwit.visionapi.Messages.TrackingOutput;

import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

public class QueueConsumer {

    private final Logger log = LogManager.getLogger(this.getClass());
    private Properties config;

    //private static String url = "tcp://localhost:61616";
    private String url;
    private String user;
    private String pw;

    private DataBaseConnection dbCon;

    private ActiveMQConnectionFactory factory;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public QueueConsumer(Properties props, DataBaseConnection dbCon) {
        this.config = props;
        this.dbCon = dbCon;
    }

    public void start() {

        url = config.getProperty("broker.url");
        user = config.getProperty("broker.username");
        pw = config.getProperty("broker.pw");

        factory = new ActiveMQConnectionFactory("tcp://" + url, user, pw);
        factory.setRetryInterval(1000);
        factory.setRetryIntervalMultiplier(1.0);
        factory.setReconnectAttempts(-1);
        factory.setConfirmationWindowSize(10);
        factory.setClientID("vision-api-consumer-01");

        log.info("set up queue connection to " 
            + "tcp://" + url + " "
            + config.getProperty("broker.queue"));

        try {
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumer = session.createConsumer(session.createQueue(config.getProperty("broker.queue")));
            consumer.setMessageListener(new TrackingMessageListener(dbCon));
            log.info("Connected to broker");
        } catch (JMSException e) {
            log.error("couldn't connect to broker " + config.getProperty("broker.url"));
        }
    }

    public void stop() {
        try {
            consumer.close();
            session.close();
            connection.stop();
        } catch (JMSException e) {
            log.warn("Closing JMS Connection didn't work " + e.getMessage());
        }

        factory.close();
    }

    private class TrackingMessageListener implements MessageListener {
        private final Logger log = LogManager.getLogger(this.getClass());

        private DataBaseConnection dbCon;

        public TrackingMessageListener(DataBaseConnection dbCon) {
            log.info("set up message listener");
            this.dbCon = dbCon;
        }

        @Override
        public void onMessage(Message message) {
            BytesMessage msg = (BytesMessage) message;
            byte[] bytes;
            try {
                bytes = new byte[(int) msg.getBodyLength()];
                msg.readBytes(bytes);
                TrackingOutput to = parseReceivedMessage(bytes);
                if(to != null) {
                    dbCon.insertNewDetection(to);
                }

            } catch (JMSException e) {
                System.out.println("Can't get bytes " + e.getMessage());
            }
        }

        private TrackingOutput parseReceivedMessage(byte[] bytes) {
            TrackingOutput to;
            try {
                to = TrackingOutput.parseFrom(bytes);
                return to;
            } catch (InvalidProtocolBufferException e) {
                System.out.println("can't parse message, returning null");
            }

            return null;
        }
    }
}