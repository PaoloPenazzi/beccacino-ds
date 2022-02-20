package it.unibo.sd.beccacino;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

// semplice thread reimpostato per interagire con RabbitMQ
public abstract class Agent extends Thread {
    public Agent(String name) {
        super(name);
    }

    public abstract void run(String myName, Connection connection) throws Exception;

    @Override
    public void run() {
        try {
            Connection connection = createConnection();
            run(getName(), connection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Connection createConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        // anche il programma client quando va a interagire con il broker lo fa sempre con un meccanismo di access-control
        // anche il client deve avere un username ed una password con cui interagire con il broker. Ovviamente qui do pieno diritto
        // di amministrazione di un utente ma in casi reali dovremmo dare le limitazioni dovute ad ogni client e usare quello
        // per far interagire il client con il broker.
        factory.setUsername("YOUR-NAME");
        factory.setPassword("YOUR-PASSWORD");
        // indirizzo della macchina che contiene il broker, nel caso online andrebbe l'url
        factory.setVirtualHost("/");
        factory.setHost("localhost");
        // porta di default rabbitmq
        factory.setPort(5672);
        // crea una connessione TCP
        return factory.newConnection();
    }
    
    protected String declareQueueForReceive(String name, Channel channel) throws IOException {
        channel.queueDeclare(name, false, false, true, null);
        return name;
    }

    protected String declareQueueForReceive(Channel channel) throws IOException {
        return channel.queueDeclare().getQueue();
    }

    protected String declareQueueForSend(String name, Channel channel) throws IOException {
        declareQueueForReceive(name, channel);
        channel.exchangeDeclare(name, BuiltinExchangeType.DIRECT);
        channel.queueBind(name, name, "");
        return name;
    }

    protected void log(String template, Object... args) {
        logImpl(System.out::println, template, args);
    }

    protected void logInline(String template, Object... args) {
        logImpl(System.out::print, template, args);
    }

    protected void logAppend(String template, Object... args) {
        var message = String.format(template, args);
        System.out.print(message);
        System.out.flush();
    }

    private void logImpl(Consumer<String> method, String template, Object... args) {
        var message = String.format(template, args);
        method.accept("[" + getName() + "] " + message);
        System.out.flush();
    }
}
