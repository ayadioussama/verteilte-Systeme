package LadenServer;

/**
 * Created by Ayadi on 23.05.2017.
 */

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

class SubscribeThread extends Thread {
    String clientID;
    HashMap<String, Long> wareNamen;

    SubscribeThread(String clientID, HashMap<String, Long> wareNamen) {
        this.clientID = clientID;
        this.wareNamen = wareNamen;
    }

    public void run() {
        System.out.println("Subscriber is running...");
        String serverURL = "tcp://broker.mqttdashboard.com:1883";
        try {
            subscribe(serverURL, wareNamen, clientID);
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    private static void subscribe(String serverURL, HashMap<String, Long> wareNamen, String clientID) throws MqttException {
        MqttClient mqttClient = new MqttClient(serverURL, //1
                clientID); //2

        mqttClient.setCallback(new MqttCallback() { //1


            public void connectionLost(Throwable throwable) {
                //Called when connection is lost.
                System.out.println("Connection is lost");
                System.out.println(throwable.getMessage());
            }

            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                System.out.println("Topic: " + topic);
                System.out.println(new String(mqttMessage.getPayload()));
                System.out.println("QoS: " + mqttMessage.getQos());
                System.out.println("Retained: " + mqttMessage.isRetained());
            }

            public void deliveryComplete(final IMqttDeliveryToken iMqttDeliveryToken) {
                //When message delivery was complete
                System.out.println("deliveryComplete");
                System.out.println(iMqttDeliveryToken);
            }
        });

        mqttClient.connect();
        for (String s : wareNamen.keySet()) {
            mqttClient.subscribe(s, 2); //2
        }
    }

}

public class LadenServer {


    public static void main(String[] args) {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        HashMap<String, Long> priceList = new HashMap<String, Long>();
        HashMap<String, Integer> capacityList = new HashMap<String, Integer>();
        String ladenName = "";
        try {
            System.out.println("Laden Name:");
            ladenName = inFromUser.readLine();
            for (int i = 0; i < 3; i++) {
                System.out.println("Ware Name:");
                String wareName = inFromUser.readLine();
                System.out.println("Ware Preis:");
                Long warePreis = Long.valueOf(inFromUser.readLine());
                priceList.put(wareName, warePreis);
                System.out.println("Ware Capacity:");
                int wareCapacity = Integer.valueOf(inFromUser.readLine());
                capacityList.put(wareName, wareCapacity);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        LadenServer(new LadenService.Processor<LadenServiceHandler>(new LadenServiceHandler(priceList, ladenName, capacityList)), priceList);
    }

    public static void LadenServer(LadenService.Processor<LadenServiceHandler> processor, HashMap<String, Long> priceList) {
        try {
            System.out.println("Geben Sie bitte LadenServerPort(1100,1101,1102):");
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            int port = Integer.parseInt(inFromUser.readLine());
            TServerTransport serverTransport = new TServerSocket(port);
            //TServer server = new TSimpleServer(
            //        new Args(serverTransport).processor(processor));

            // Use this for a multithreaded server
            TServer server = new TThreadPoolServer(new
                    TThreadPoolServer.Args(serverTransport).processor(processor));

            System.out.println("Starting the simple server...");
            // I m STH schreiben wir clientID und Topic(liste von Waren)
            SubscribeThread t1 = new SubscribeThread("Laden" + port, priceList);
            t1.start();
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
