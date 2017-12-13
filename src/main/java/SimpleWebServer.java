
import org.apache.thrift.TException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

class SimpleWebServer implements HttpConstants {


    protected static Properties props = new Properties();

    /* Where worker threads stand idle */
    static Vector threads = new Vector();

    /* the web server's virtual root */
    static File root;

    /* timeout on client connections */
    static int timeout = 0;

    /* max # worker threads */
    static int workers = 5;


    /* load www-server.properties from java.home */
    static void loadProps() throws IOException {
        File f = new File
                (System.getProperty("java.home") + File.separator +
                        "lib" + File.separator + "www-server.properties");
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new
                    FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("root");
            if (r != null) {
                root = new File(r);
                if (!root.exists()) {
                    throw new Error(root + " doesn't exist as server root");
                }
            }
            r = props.getProperty("timeout");
            if (r != null) {
                timeout = Integer.parseInt(r);
            }
            r = props.getProperty("workers");
            if (r != null) {
                workers = Integer.parseInt(r);
            }
            r = props.getProperty("log");
        }

        /* if no properties were specified, choose defaults */
        if (root == null) {
            root = new File(System.getProperty("user.dir"));
        }
        if (timeout <= 1000) {
            timeout = 5000;
        }
        if (workers < 25) {
            workers = 5;
        }
    }

    public static void main(String[] a) throws Exception {
        int port = 9090;

        if (a.length > 0) {
            port = Integer.parseInt(a[0]);
        }
        loadProps();
        /* start worker threads */
        for (int i = 0; i < workers; ++i) {
            Worker w = new Worker();
            (new Thread(w, "worker #" + i)).start();
            threads.addElement(w);
        }

        ServerSocket ss = new ServerSocket(port);
        while (true) {

            Socket s = ss.accept();

            Worker w = null;
            synchronized (threads) {
                if (threads.isEmpty()) {
                    Worker ws = new Worker();
                    ws.setSocket(s);
                    (new Thread(ws, "additional worker")).start();
                } else {
                    w = (Worker) threads.elementAt(0);
                    threads.removeElementAt(0);
                    w.setSocket(s);
                }
            }
        }
    }
}

class Worker extends SimpleWebServer implements HttpConstants, Runnable {
    final static int BUF_SIZE = 2048;

    static final byte[] EOL = {(byte) '\r', (byte) '\n'};

    /* buffer to use for requests */
    byte[] buf;
    /* Socket to client we're handling */
    private Socket s;

    Worker() {
        buf = new byte[BUF_SIZE];
        s = null;
    }

    synchronized void setSocket(Socket s) {
        this.s = s;
        notify();
    }


    public synchronized void run() {
        while (true) {
            if (s == null) {
                /* nothing to do */
                try {
                    wait();
                } catch (InterruptedException e) {
                    /* should not happen */
                    continue;
                }
            }
            try {


                handleClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            /* go back in wait queue if there's fewer
             * than numHandler connections.
             */
            s = null;
            Vector pool = SimpleWebServer.threads;
            synchronized (pool) {
                if (pool.size() >= SimpleWebServer.workers) {
                    /* too many threads, exit this one */
                    return;
                } else {
                    pool.addElement(this);
                }
            }
        }
    }

    void handleClient() throws IOException, InterruptedException {
        InputStream is = new BufferedInputStream(s.getInputStream());
        PrintStream ps = new PrintStream(s.getOutputStream());
        /* we will only block in read for this many milliseconds
         * before we fail with java.io.InterruptedIOException,
         * at which point we will abandon the connection.
         */
        // s.setSoTimeout(SimpleWebServer.timeout);
        s.setTcpNoDelay(true);
        /* zero out the buffer from last time */
        for (int i = 0; i < BUF_SIZE; i++) {
            buf[i] = 0;
        }
        try {
            /* We only support HTTP GET/HEAD, and don't
             * support any fancy HTTP options,
             * so we're only interested really in
             * the first line.
             */
            int nread = 0, r = 0;

            outerloop:
            while (nread < BUF_SIZE) {
                r = is.read(buf, nread, BUF_SIZE - nread);
                if (r == -1) {
                    /* EOF */
                    return;
                }
                int i = nread;
                nread += r;
                for (; i < nread; i++) {
                    if (buf[i] == (byte) '\n' || buf[i] == (byte) '\r') {
                        /* read one line */
                        break outerloop;
                    }
                }
            }
            String h = new String(buf, 0, nread);
            //log("..." + h);
            /* are we doing a GET or just a HEAD */
            boolean doingGet;
            /* beginning of file name */
            int index;
            if (buf[0] == (byte) 'G' &&
                    buf[1] == (byte) 'E' &&
                    buf[2] == (byte) 'T' &&
                    buf[3] == (byte) ' ') {
                doingGet = true;
                index = 4;
            } else if (buf[0] == (byte) 'H' &&
                    buf[1] == (byte) 'E' &&
                    buf[2] == (byte) 'A' &&
                    buf[3] == (byte) 'D' &&
                    buf[4] == (byte) ' ') {
                doingGet = false;
                index = 5;
            } else {
                ps.print("HTTP/1.0 " + HTTP_BAD_METHOD +
                        " unsupported method type: ");
                ps.write(buf, 0, 5);
                ps.write(EOL);
                ps.flush();
                s.close();
                return;
            }
//------------------------------------------------------------------------------
            String msg = "";
            if (doingGet && (h.split("\r\n")[0]).length() > 14) {

                HashMap<String, String> vars = new HashMap<String, String>();
                for (String varPair : (h.split("\r\n")[0]).substring((h.split("\r\n")[0]).indexOf("?") + 1, (h.split("\r\n")[0]).indexOf(" HTTP/1.1")).split("&")) {
                    if (varPair.split("=").length != 2) continue;
                    vars.put(varPair.split("=")[0], (varPair.split("=")[1]).trim());
                }

                if (vars.size() != 0 && vars.get("ware") != null && !vars.get("ware").equals("")) {
                    try {
                        System.out.println("bestellt : " + UDPServer.bestellen(vars.get("ware"), Integer.parseInt(vars.get("menge")), vars.get("datum")));
                    } catch (TException e) {
                        e.printStackTrace();
                    }

                }

            }
//------------------------------------------------------------------------------

            int i = 0;
            /* find the file name, from:
             * GET /foo/bar.html HTTP/1.0
             * extract "/foo/bar.html"
             */
            for (i = index; i < nread; i++) {
                if (buf[i] == (byte) ' ') {
                    break;
                }
            }

            String fname = (new String(buf, 0, index, (h.indexOf("?") > 0 ? h.indexOf("?") : i) - index)).replace('/', File.separatorChar);


            if (fname.startsWith(File.separator)) {
                fname = fname.substring(1);
            }
            File targ = new File(SimpleWebServer.root, fname);
            if (targ.isDirectory()) {
                File ind = new File(targ, "index.html");
                if (ind.exists()) {
                    targ = ind;
                }
            }
            boolean OK = printHeaders(targ, ps, msg);
            if (doingGet) {
                if (OK) {
                    sendFile(targ, ps, msg);
                } else {
                    send404(targ, ps);
                }
            }
        } finally {
            s.close();
        }
    }


    boolean printHeaders(File targ, PrintStream ps, String msg) throws IOException {
        boolean ret = false;
        int rCode = 0;
        if (!targ.exists()) {
            rCode = HTTP_NOT_FOUND;
            ps.print("HTTP/1.0 " + HTTP_NOT_FOUND + " not found");
            ps.write(EOL);
            ret = false;
        } else {
            rCode = HTTP_OK;
            ps.print("HTTP/1.0 " + HTTP_OK + " OK");
            ps.write(EOL);
            ret = true;
        }
        ps.print("Server: VS Praktikum 2");
        ps.write(EOL);
        ps.print("Date: " + (new Date()));
        ps.write(EOL);
        if (ret) {
            if (!msg.equals("")) {
                ps.print("Content-type: text/html");
                ps.write(EOL);
            } else if (!targ.isDirectory()) {
                ps.print("Content-length: " + targ.length());
                ps.write(EOL);
                ps.print("Last Modified: " + (new
                        Date(targ.lastModified())));
                ps.write(EOL);
                String name = targ.getName();
                int ind = name.lastIndexOf('.');
                String ct = null;
                if (ind > 0) {
                    ct = (String) map.get(name.substring(ind));
                }
                if (ct == null) {
                    ct = "unknown/unknown";
                }
                ps.print("Content-type: " + ct);
                ps.write(EOL);
            } else {
                ps.print("Content-type: text/html");
                ps.write(EOL);
            }
        }
        return ret;
    }

    void send404(File targ, PrintStream ps) throws IOException {
        ps.write(EOL);
        ps.write(EOL);
        ps.println("Not Found\n\n" +
                "The requested resource was not found.\n");
    }

    void sendFile(File targ, PrintStream ps, String msg) throws IOException {
        InputStream is = null;
        ps.write(EOL);
        if (targ.isDirectory()) {
            //listDirectory(targ, ps);
            sendSensorStatus(targ, ps);
            return;
        } else {
            is = new FileInputStream(targ.getAbsolutePath());
        }

        try {
            if (msg.equals("")) {
                int n;
                while ((n = is.read(buf)) > 0) {
                    ps.write(buf, 0, n);
                }
            } else {
                ps.write(msg.getBytes());
            }
        } finally {
            is.close();
        }
    }

    /* mapping of file extensions to content-types */
    static java.util.Hashtable map = new java.util.Hashtable();


    void sendSensorStatus(File dir, PrintStream ps) throws IOException {
        ps.println("<TITLE>Sensoren zustand</TITLE><P>\n");
        ps.println("<table border=1><tr><td><b>Fuellstand</b></td><td><b>Ware</b></td></tr>\n");
        for (String key : UDPServer.sensorsAktuell.keySet()) {
            ps.println("<tr><td>" + key + "</td><td>" + UDPServer.sensorsAktuell.get(key) + "</td></tr>\n");
        }
        ps.println("</table>\n");
        ps.println("<hr>\n");
        ps.println("<form method='get' action='/'><b>Ware:</b><input type='text' name='ware'><br>" +
                "<b>Menge</b><input type='text' name='menge'><br>" +
                "<b>nachzubestellenDatum</b><input type='text' name='datum'><br>" +
                "<input type='submit' value='bestellen'>" +
                "</form><hr>\n");
        ps.println("<br><table border=1><tr><td><b>History</b></td></tr>\n");
        ps.println("<tr><td><b>PacketNummer</b></td><td><b>Ware</b></td><td><b>Fullstand</b></td><td><b>verschickt Um</b></td><td><b>angekommen Um</b></td></tr>\n");
        for (int i = UDPServer.history.size() - 1; i >= 0; i--) {
            String log = UDPServer.history.get(i);
            ps.println("<tr>");
            for (String s : log.split(";")) {
                ps.println("<td>" + s + "</td>\n");
            }
            ps.println("</tr>");
        }

        ps.println("</table>\n");

    }

}

interface HttpConstants {
    /**
     * 2XX: generally "OK"
     */
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_ACCEPTED = 202;
    public static final int HTTP_NOT_AUTHORITATIVE = 203;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_RESET = 205;
    public static final int HTTP_PARTIAL = 206;

    /**
     * 3XX: relocation/redirect
     */
    public static final int HTTP_MULT_CHOICE = 300;
    public static final int HTTP_MOVED_PERM = 301;
    public static final int HTTP_MOVED_TEMP = 302;
    public static final int HTTP_SEE_OTHER = 303;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_USE_PROXY = 305;

    /**
     * 4XX: client error
     */
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_PAYMENT_REQUIRED = 402;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_METHOD = 405;
    public static final int HTTP_NOT_ACCEPTABLE = 406;
    public static final int HTTP_PROXY_AUTH = 407;
    public static final int HTTP_CLIENT_TIMEOUT = 408;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_GONE = 410;
    public static final int HTTP_LENGTH_REQUIRED = 411;
    public static final int HTTP_PRECON_FAILED = 412;
    public static final int HTTP_ENTITY_TOO_LARGE = 413;
    public static final int HTTP_REQ_TOO_LONG = 414;
    public static final int HTTP_UNSUPPORTED_TYPE = 415;

    /**
     * 5XX: server error
     */
    public static final int HTTP_SERVER_ERROR = 500;
    public static final int HTTP_INTERNAL_ERROR = 501;
    public static final int HTTP_BAD_GATEWAY = 502;
    public static final int HTTP_UNAVAILABLE = 503;
    public static final int HTTP_GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION = 505;
}