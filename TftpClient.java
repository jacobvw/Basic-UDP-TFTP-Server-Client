import java.io.*;
import java.net.*;

class TftpClient {
  private static final byte RRQ = 1;
  private static final byte DATA = 2;
  private static final byte ACK = 3;
  private static final byte ERROR = 4;

  private static final int PACKETSIZE = 514;

  private static final int BYTESIZE = 255;
  private static final int TIMEOUT = 5000;

  private static final boolean VERBOSE = true;

  private static DatagramSocket clientSocket;
  private static InetAddress IPAddress;
  private static int port;
  private static String filename;
  private static String destination;

  public static void main(String args[]) throws Exception {
    if(args.length != 4) {
      System.err.println("Usage: java TftpClient <hostname> <port> <filename> <destination>");
      return;
    }

    clientSocket = new DatagramSocket();		// initialise Datagram socket
    clientSocket.setSoTimeout(TIMEOUT);			// set socket timeout

    try {
      IPAddress = InetAddress.getByName(args[0]);
      port = Integer.parseInt(args[1]);
      filename = args[2];
      destination = args[3];
    } catch(Exception ex) { System.err.println("Invalid input"); return; }

    // send request for file
    sendRRQ(filename);

    // open the destination output file
    File file = new File(destination);
    FileOutputStream stream = new FileOutputStream(file);

    byte block = 1;
    byte lastBlock = 0;

    while(true) {
      // wait for the data packet
      byte[] receiveData = new byte[PACKETSIZE];
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

      // wait for a response. if we dont get 1 in the TIMEOUT period cancel
      try { clientSocket.receive(receivePacket); }
      catch(SocketTimeoutException ex) {
        System.err.println("SERVER TIMED OUT OR CONNECTION CLOSED");
        return;
      }

      // check if the response if a valid data packet
      if(receiveData[0] == DATA) {

        // check if block received is block requested
        if(receiveData[1] == block) {
          // ack the packet
          sendACK(receivePacket, receiveData[1]);
          if(VERBOSE) System.out.println("RECEIVED BLOCK: " + Byte.toUnsignedInt(receiveData[1]));

          // write the bytes to a file
          stream.write(receiveData, 2, receivePacket.getLength()-2);

          // set the lastBlock received to the most recently received block
          lastBlock = block;
          // if the byte size has reached its max reset the block
          if(Byte.toUnsignedInt(block) >= BYTESIZE) {
            block = 0;
          }
          // increment block
          block++;
        // else we received a packet we were not expecting
        } else {
          // send ack for the last packet we expected. This occurs when the last ack packet got lost in the network
          sendACK(receivePacket, lastBlock);
        }


        // if the packet was smaller than 514 bytes it is the last one. If sent a EOF packet should be 2 bytes so this condition still works
        if(receivePacket.getLength() < PACKETSIZE) break;

      } else {
        System.err.println(new String(receivePacket.getData(), 1, receivePacket.getLength()-1));
        break;
      }
    }

    // close the output file and socket
    stream.close();
    clientSocket.close();

    System.out.println("RECEIVED " + filename + " SUCCESSFULLY");
  }

  // method used to send udp ack packets
  public static void sendACK(DatagramPacket receivePacket, byte block) {
    try {
      byte[] sendData = new byte[2];
      sendData[0] = ACK;
      sendData[1] = block;
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
      clientSocket.send(sendPacket);
    } catch (Exception ex) { System.err.println(ex); }
  }
  // method used to send udp rrq packets
  public static void sendRRQ(String filename) {
    try {
      byte[] a = new byte[1];
      byte[] b = new byte[filename.length()];
      a[0] = RRQ;
      b = filename.getBytes();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(a);
      outputStream.write(b);
      byte[] sendData = outputStream.toByteArray();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
      clientSocket.send(sendPacket);
    } catch(Exception ex) { System.err.println(ex); }
  }
}
