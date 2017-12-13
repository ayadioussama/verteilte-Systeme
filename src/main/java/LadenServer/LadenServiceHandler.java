package LadenServer;

/**
 * Created by Ayadi on 23.05.2017.
 */


import org.apache.thrift.TException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LadenServiceHandler implements LadenService.Iface {
    private HashMap<String, Long> priceList = new HashMap<String, Long>();
    private List<Order> orders = new ArrayList<Order>();
    private static String logFileName = "LadenLog.csv";
    private String ladenName;
    private HashMap<String, Integer> capacityList;

    LadenServiceHandler(HashMap<String, Long> priceList, String ladenName, HashMap<String, Integer> capacityList) {
        this.priceList = priceList;
        this.ladenName = ladenName;
        this.capacityList = capacityList;
    }


    public long getPrice(String articleName) throws TException {
        Long articlePrice = priceList.get(articleName);
        return articlePrice == null ? -1 : articlePrice;
    }

    public int orderArticle(String articleName, String receiveDate, String customer, int amount) throws TException {
        Order order = new Order();
        order.customer = customer;
        order.article = articleName;
        order.receiveDate = receiveDate;
        order.amount = amount;
        orders.add(order);
        //toDo: wenn recAmount mehr als aktuelle Ware ist, muss weniger gekauft werden, oder ...
        System.out.println("aktuell Menge " + capacityList.get(articleName));
        System.out.println("Neue Bestellung von " + customer + " Ware:" + articleName + " Menge:" + amount + " Datum:" + receiveDate);
        int newCapacity = capacityList.get(articleName) - amount;
        capacityList.remove(articleName);
        capacityList.put(articleName, newCapacity);
        //p4
        if (newCapacity < 100) {
            int bestellungsMenge = 100;
            capacityList.remove(articleName);
            capacityList.put(articleName, newCapacity + bestellungsMenge);
            System.out.println("Von produzent bestellt, aktuelle: " + (newCapacity + bestellungsMenge));
            String serverURL = "tcp://broker.mqttdashboard.com:1883";


            PublishThread t1 = new PublishThread(serverURL, articleName + "Need", ladenName + ";" + articleName + ";" + bestellungsMenge);
            t1.start();
        }
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(logFileName, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedWriter bw1 = new BufferedWriter(fileWriter);
        PrintWriter out1 = new PrintWriter(bw1);
        out1.println("laden: " + ladenName + " kunde:" + customer + " Ware:" + articleName + " Menge:" + amount + " Datum:" + receiveDate + " preis:" + amount * priceList.get(articleName));
        out1.close();
        return amount;
    }
}

class PublishThread extends Thread {
    String serverURL;
    String produktName;
    String produktsMenge;

    PublishThread(String serverURL, String produktName, String produktsMenge) {
        this.serverURL = serverURL;
        this.produktName = produktName;
        this.produktsMenge = produktsMenge;
    }

    public void run() {
        System.out.println("Subscriber is running...");

        try {
            publish(serverURL, produktName, produktsMenge, produktName + " Produzent");

        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    private static void publish(String serverURL, String topic, String msg, String clientID) throws MqttException {
        MqttClient mqttClient = new MqttClient(serverURL, //1
                clientID); //2

        mqttClient.connect();

        mqttClient.publish(topic, //3
                msg.getBytes(), //4
                0, //5
                false); //6
        mqttClient.close();
    }
}

class Order {
    String code;
    String customer;
    String article;
    String receiveDate;
    int amount;
}