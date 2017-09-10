import java.net.*;
import java.io.*;
import java.util.*;

class TftpServerWorker extends Thread {
    private DatagramPacket req;

    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;

    private static final int PACKETSIZE = 514;
    private static final int BYTESIZE = 255;
    private static final int TIMEOUT = 1000;

    private static final boolean VERBOSE = true;

    private void sendfile(String filename) {
        System.out.println("FILE " + filename + " REQUESTED");

        // get the file
        File file = new File(filename.trim());

        // check if it exists
        if(file.exists()) {
          try {
            FileInputStream stream = new FileInputStream(file);
            byte block = 1;
            byte[] sendData = new byte[PACKETSIZE];
            DatagramSocket clientSocket = new DatagramSocket();
            boolean sendEOF = false;
            boolean lastRun = false;

            clientSocket.setSoTimeout(TIMEOUT);

            // if the file is a multiple of 512 (514 with headers) a EOF needs to be sent
            if((stream.available()%(PACKETSIZE-2)) == 0) sendEOF = true;

            // loop until the file has been fully read
            while(stream.available() >= 0) {

              // handles condition if file was a multiple of 512bytes (514 bytes if you include the headers) to send EOF
              if(stream.available() == 0) {
                if(!sendEOF) break;
                else lastRun = true;
              }

              int packetSize = Math.min(stream.available(), PACKETSIZE-2);

              sendData = new byte[packetSize+2];
              sendData[0] = DATA;
              sendData[1] = block;
              stream.read(sendData, 2, sendData.length-2);

              // create the packet to send
              DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, req.getAddress(), req.getPort());

              // setup the ack packet to be returned after data has been sent
              byte[] ack = new byte[2];
              DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);

              boolean ackReceived = false;
              int transmitCount = 0;
              while(!ackReceived) {
                try {
                  // send the data packet and wait for the ack
                  clientSocket.send(sendPacket);
                  clientSocket.receive(receivePacket);

                  if(VERBOSE) System.out.println("RECEIVED ACK FOR BLOCK: " + Byte.toUnsignedInt(ack[1]));

                  // only flag the data packet as received if the ack was for the sent packet
                  if(block == ack[1]) ackReceived = true;
                } catch(SocketTimeoutException ex) {
                  if(VERBOSE) System.out.println("ACK NOT RECEIVED. RESENDING BLOCK: " + Byte.toUnsignedInt(sendData[1]));

                  // increment the count for number of failed packet transmissions for the current data packet
                  transmitCount++;
                  // if it has failed 5 or more times abort the transfer
                  if(transmitCount >= 5) {
                    System.err.println("FAILED TO GET ACK FOR BLOCK " + Byte.toUnsignedInt(sendData[1]) + " AFTER " + transmitCount + " ATTEMPTS. ABORTING");
                    return;
                  }
                }
              }

              // if the maximum byte size has been reached reset it back to 0
              if (Byte.toUnsignedInt(block) >= BYTESIZE) block = 0;
              // increment to block count
              block++;

              if(lastRun) break;
            }

            System.out.println("FILE " + filename + " TRANSFERED SUCCESSFULLY");

          } catch (Exception ex) { System.out.println("Exception - " + ex); }

        // if the file does not exist. send error packet
        } else {
          try {
            // represents "4file not found" in utf8
            byte[] sendData = new byte[15];
            sendData[0] = ERROR;
            sendData[1] = 102;
            sendData[2] = 105;
            sendData[3] = 108;
            sendData[4] = 101;
            sendData[5] = 32;
            sendData[6] = 110;
            sendData[7] = 111;
            sendData[8] = 116;
            sendData[9] = 32;
            sendData[10] = 102;
            sendData[11] = 111;
            sendData[12] = 117;
            sendData[13] = 110;
            sendData[14] = 100;
            DatagramSocket clientSocket = new DatagramSocket();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, req.getAddress(), req.getPort());

            clientSocket.send(sendPacket);
            clientSocket.close();

            System.out.println("FILE " + filename + " NOT FOUND, TRANSFER FAILED");
          } catch (Exception ex) { System.out.println("Exception - " + ex); }
        }

	return;
    }

    public void run() {
        byte[] data = req.getData();
        String filename = new String(req.getData()).substring(1);

        if(data[0] == RRQ) {
		sendfile(filename);
        }

	return;
    }

    public TftpServerWorker(DatagramPacket req) {
	this.req = req;
    }
}

class TftpServer {
    public void start_server() {
	try {
	    DatagramSocket ds = new DatagramSocket();
	    System.out.println("TftpServer on port " + ds.getLocalPort());

	    for(;;) {
		byte[] buf = new byte[1472];
		DatagramPacket p = new DatagramPacket(buf, 1472);
		ds.receive(p);

		TftpServerWorker worker = new TftpServerWorker(p);
		worker.start();
	    }
	}
	catch(Exception e) {
	    System.err.println("Exception: " + e);
	}
	return;
    }

    public static void main(String args[]) {
	TftpServer d = new TftpServer();
	d.start_server();
    }
}
