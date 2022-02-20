/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package it.unibo.sd.beccacino;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.bson.Document;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class App {
    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        Listener listener = new Listener("listener");
        Sender sender = new Sender("sender", "test message");
        listener.start();
        sender.start();
        try {
            listener.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        try {
            sender.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
