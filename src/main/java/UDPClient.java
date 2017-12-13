import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;


class Sensor {
    public String name;
    public Integer capacity = 100;
}

class UDPClient {
    public static void main(String args[]) throws Exception {
        // for (int i = 0; i < 4; i++) {
        System.out.println("Geben Sie bitte Artikelname:");
        Sensor sensor = new Sensor();
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        sensor.name = inFromUser.readLine();

        System.out.println("Geben Sie bitte füllstand");
        sensor.capacity = Integer.parseInt(inFromUser.readLine());
        //Thread zum schicken von Füllstand
        sendQuery m1 = new sendQuery(sensor);
        Thread t1 = new Thread(m1);
        t1.start();
        //Thread zum reduzieren von Füllstand
        reduceCapacity m2 = new reduceCapacity(sensor);
        Thread t2 = new Thread(m2);
        t2.start();
        //  }
    }
}


class sendQuery implements Runnable {
    Sensor sensor;
    int counter = 1;


    sendQuery(Sensor sensor) {
        this.sensor = sensor;
    }

    public void run() {
        while (true) {
            try {
                // schickt jede 3 seconde eine sentence(sensorname+füllstand)
                // TimeUnit.NANOSECONDS.sleep(1); // 1nano s warten
                TimeUnit.SECONDS.sleep(5); // 5s warten

                //BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
                DatagramSocket clientSocket = new DatagramSocket();
                //InetAddress IPAddress = InetAddress.getByName("172.16.201.101");
                InetAddress IPAddress = InetAddress.getByName("localhost");
                byte[] sendData = new byte[1024];
                byte[] receiveData = new byte[1024];
                String sentence;

                sentence = (counter++) + ";" + sensor.name + ";" + sensor.capacity + ";" + new Timestamp(System.currentTimeMillis());
                // System.out.println(sentence);

                sendData = sentence.getBytes();

                //sendData = "nal".getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
                clientSocket.send(sendPacket);


                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);
                String modifiedSentence = new String(receivePacket.getData());
                System.out.println("FROM SERVER:" + modifiedSentence);
                sensor.capacity += Integer.parseInt(modifiedSentence.trim());

                clientSocket.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class reduceCapacity implements Runnable {

    Sensor sensor;


    reduceCapacity(Sensor sensor) {
        this.sensor = sensor;
    }

    public void run() {
        while (true) {
            try {
                // TimeUnit.NANOSECONDS.sleep(1); // 1 nano s warten
                TimeUnit.SECONDS.sleep(5);//1 sekunde warten
                //     System.out.println("Old Capacity:   " + sensor.capacity);

                sensor.capacity -= 2;
                if (sensor.capacity < 0) {
                    sensor.capacity = 0;
                }

                // System.out.println("New Capacity:   " + sensor.capacity);


            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

