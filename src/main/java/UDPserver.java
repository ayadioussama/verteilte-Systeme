import LadenServer.LadenService;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

class TempThread extends Thread {
    public void run() {
        System.out.println("Webserver is running...");
        String[] argstmp = {};
        try {
            SimpleWebServer.main(argstmp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class UDPServer {
    static HashMap<String, String> sensorsAktuell = new HashMap<String, String>();
    static HashMap<String, Integer> sensorsOnlineBestellung = new HashMap<String, Integer>();
    static List<String> history = new ArrayList<String>();
    private static String logFileName = "logSensors.csv";
    public static String serverName = "Kühlschrank1";
    public static int wenigerAlsProzent = 10;

    public static void main(String args[]) throws Exception {
        // Erzeugung einer Sockets
        loadLogs();
        TempThread t1 = new TempThread();
        t1.start();

        DatagramSocket serverSocket = new DatagramSocket(9876);
        System.out.println("Server Startet, Wartet auf Data....");
        FileWriter fw1 = new FileWriter(logFileName, true);
        BufferedWriter bw1 = new BufferedWriter(fw1);
        PrintWriter out1 = new PrintWriter(bw1);
        out1.println("Packetnummer;Ware;fuellstand;veschickt um;angekommen um");
        out1.close();
        while (true) {
            byte[] receiveData = new byte[1024];
            byte[] sendData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            String sentence = new String(receivePacket.getData());
            //Leerzeichen weg macghen
            System.out.println("RECEIVED: " + sentence.trim() + ";" + new Timestamp(System.currentTimeMillis()));
            addSensorsAktuell(sentence.trim());

            //logfile write
            FileWriter fw = new FileWriter(logFileName, true);

            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            String log = (sentence.trim() + ";" + new Timestamp(System.currentTimeMillis()));
            history.add(log);
            out.println(log);

            //checkUndBestellen neue Ware
            String ware = sentence.split(";")[1];
            int aktuelleProz = Integer.parseInt(sentence.split(";")[2]);

            int onlineBestellungsMenge = 0;
            if (sensorsOnlineBestellung.get(ware) != null && sensorsOnlineBestellung.get(ware) != 0) {
                onlineBestellungsMenge = sensorsOnlineBestellung.get(ware);
                sensorsOnlineBestellung.remove(ware);
            }
            int neueWareMenge = checkUndBestellen(ware, aktuelleProz) + onlineBestellungsMenge;
            //update Sensor
            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();
            String capitalizedSentence = String.valueOf(neueWareMenge);
            sendData = capitalizedSentence.getBytes();
            DatagramPacket sendPacket =
                    new DatagramPacket(sendData, sendData.length, IPAddress, port);
            serverSocket.send(sendPacket);

            out.close();
        }
    }

    private static int checkUndBestellen(String ware, int aktuelleProz) {

        int guenstigLaden = 0;
        int bestellungsMenge = 0;
        if (aktuelleProz > wenigerAlsProzent) {
            return 0;
        }

        try {
            //günstige laden finden
            //toDo: Error and wenn port=0 ist bedeutet keine laden hat ware
            guenstigLaden = getBestLaden(ware);
            //guenstigLaden = 1100;

            //checkUndBestellen
            bestellungsMenge = bestellen(ware, guenstigLaden);

        } catch (TException e) {
            e.printStackTrace();
        }
        return bestellungsMenge;
    }

    // p3 - günstiges Laden
    private static int getBestLaden(String ware) throws TException {
        ArrayList<Integer> ladenPorts = new ArrayList<Integer>();
        ladenPorts.add(1100);
        ladenPorts.add(1101);
        ladenPorts.add(1102);
        long guenstigPreis = 0;
        int guenstigLaden = 0;
        for (Integer ladenPort : ladenPorts) {
            TTransport transport;
            transport = new TSocket("localhost", ladenPort);
            transport.open();

            TProtocol protocol = new TBinaryProtocol(transport);
            LadenService.Client client = new LadenService.Client(protocol);
            long newPreis = client.getPrice(ware.toLowerCase().trim());
            if (newPreis == -1) continue;
            if (guenstigPreis == 0) {
                guenstigPreis = newPreis;
                guenstigLaden = ladenPort;
                continue;
            }
            if (newPreis > 0 && guenstigPreis > newPreis) {
                guenstigPreis = newPreis;
                guenstigLaden = ladenPort;
            }
            transport.close();
        }
        System.out.println("ServerLaden mit port " + guenstigLaden + " hat günstige Preis:" + guenstigPreis);

        return guenstigLaden;
    }

    private static Integer bestellen(String ware, int guenstigLaden) throws TException {
        int bestellungsMenge;

        TTransport transport;
        transport = new TSocket("localhost", guenstigLaden);
        transport.open();

        TProtocol protocol = new TBinaryProtocol(transport);
        LadenService.Client client = new LadenService.Client(protocol);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        bestellungsMenge = client.orderArticle(ware.toLowerCase().trim(), dateFormat.format(cal.getTime()), serverName, (100 - wenigerAlsProzent));

        transport.close();

        return bestellungsMenge;
    }

    public static Integer bestellen(String ware, int menge, String nachzubestellenDatum) throws TException {
        int bestellungsMenge;

        TTransport transport;

        transport = new TSocket("localhost", getBestLaden(ware));
        //transport = new TSocket("localhost", 1100);
        transport.open();

        TProtocol protocol = new TBinaryProtocol(transport);
        LadenService.Client client = new LadenService.Client(protocol);

        bestellungsMenge = client.orderArticle(ware.toLowerCase().trim(), nachzubestellenDatum, serverName, menge);

        if (sensorsOnlineBestellung.get(ware) == null) {
            sensorsOnlineBestellung.put(ware, bestellungsMenge);
        } else {
            int neueMenge = sensorsOnlineBestellung.get(ware) + bestellungsMenge;
            sensorsOnlineBestellung.remove(ware);
            sensorsOnlineBestellung.put(ware, neueMenge);
        }
        transport.close();

        return bestellungsMenge;
    }


    private static void loadLogs() throws IOException {
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(logFileName));
            String line = br.readLine();
            while (line != null) {
                if (!line.equals("Packetnummer;Ware;fuellstand;veschickt um;angekommen um")) {
                    history.add(line);
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void addSensorsAktuell(String sentence) {
        String[] values = sentence.split(";");
        if (sensorsAktuell.get(values[1]) == null) {
            sensorsAktuell.put(values[1], values[2]);
        } else {
            sensorsAktuell.remove(values[1]);
            sensorsAktuell.put(values[1], values[2]);
        }
    }
}