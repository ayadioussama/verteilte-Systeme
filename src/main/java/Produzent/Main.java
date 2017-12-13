package Produzent;

import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    static class PublishThread extends Thread {
        String serverURL;
        String produktName;
        String produktPreis;
        String clientID;

        PublishThread(String serverURL, String produktName, String produktPreis, String clientID) {
            this.serverURL = serverURL;
            this.produktName = produktName;
            this.produktPreis = produktPreis;
            this.clientID = clientID;
        }

        public void run() {
            System.out.println("Publisher is running...");

            try {
                publish(serverURL, produktName, produktPreis, clientID);

            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    static class SubscribeThread extends Thread {
        String clientID;
        String wareName;
        String serverURL;

        SubscribeThread(String clientID, String wareName, String serverURL) {
            this.clientID = clientID;
            this.wareName = wareName;
            this.serverURL = serverURL;
        }

        public void run() {
            System.out.println("Subscriber is running...");
            try {
                subscribe(serverURL, wareName, clientID);
            } catch (MqttException e) {
                e.printStackTrace();
            }

        }

        private void subscribe(String serverURL, String wareName, String clientID) throws MqttException {
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
            mqttClient.subscribe(wareName, 2); //2

        }

    }

        public static void main(String[] args) throws MqttException, IOException {
        //Server zwische Publisher und Subscriber
        String serverURL = "tcp://broker.mqttdashboard.com:1883";
        String produktName = "";
        String produktPreis = "";
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Produkt Name:");
        produktName = inFromUser.readLine();
        System.out.println("Produkt Preis:");
        produktPreis = inFromUser.readLine();

        PublishThread t1 = new PublishThread(serverURL, produktName, produktPreis, produktName + " Produzent");
        t1.start();
        SubscribeThread t2 = new SubscribeThread(produktName + " Produzent", produktName + "Need", serverURL);
        t2.start();
        //subscribe(serverURL, "Hallo MQTT TOPIC", "cli2");

    }


    //publish bekommt die URL Broker client ID
    private static void publish(String serverURL, String topic, String msg, String clientID) throws MqttException {
        MqttClient mqttClient = new MqttClient(serverURL, //1
                clientID); //2

        mqttClient.connect();

        mqttClient.publish(topic, //3
                msg.getBytes(), //4
                0, //5   qualite of service
                false); //6
        mqttClient.close();
    }
}
