package socs.network.node;

import socs.network.util.Configuration;
import socs.network.message.SOSPFPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];
  // The listener handles attach and start messages while terminal() reads commands.
  private ServerSocket serverSocket;
  // Show only one attach approval prompt at a time.
  private final Object requestLock = new Object();
  private volatile AttachRequest pendingAttach;

  private static class AttachRequest {
    final CountDownLatch answered = new CountDownLatch(1);
    boolean accepted;
  }

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    lsd = new LinkStateDatabase(rd);

    try {
      InetAddress localAddress = InetAddress.getLocalHost();
      // Port 0 asks the operating system to choose an available listening port.
      serverSocket = new ServerSocket(0, 50, localAddress);
      rd.processIPAddress = localAddress.getHostAddress();
      rd.processPortNumberFull = serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start router", e);
    }

    System.out.println("========================================");
    System.out.println("Process IP : " + rd.processIPAddress);
    System.out.println("Process Port : " + rd.processPortNumberFull);
    System.out.println("Simulated IP : " + rd.simulatedIPAddress);
    System.out.println("========================================");

    Thread listener = new Thread(new Runnable() {
      public void run() {
        requestHandler();
      }
    }, "router-listener");
    listener.setDaemon(true);
    listener.start();
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {
    processAttach(processIP, (int) processPort, simulatedIP, weight);
  }

  private void processAttach(String processIP, int processPort,
                             String simulatedIP, short weight) {
    if (processPort < 1 || processPort > 65535 || weight < 0 ||
        simulatedIP.equals(rd.simulatedIPAddress)) {
      System.out.println("Invalid attach request.");
      return;
    }
    synchronized (ports) {
      if (findPort(simulatedIP) >= 0 || freePort() < 0) {
        System.out.println("Router already attached or no free port available.");
        return;
      }
    }

    try (Socket socket = new Socket(processIP, processPort)) {
      ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
      output.flush();
      ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
      // The attach HELLO carries the sender's process and simulated addresses.
      SOSPFPacket hello = new SOSPFPacket();
      hello.sospfType = 0;
      hello.helloStage = 0;
      hello.srcProcessIP = rd.processIPAddress;
      hello.srcProcessPortFull = rd.processPortNumberFull;
      hello.srcIP = rd.simulatedIPAddress;
      hello.dstIP = simulatedIP;
      hello.linkWeight = weight;
      output.writeObject(hello);
      output.flush();

      if (!input.readBoolean()) {
        System.out.println("Your attach request has been rejected;");
        return;
      }
      RouterDescription remote = new RouterDescription();
      remote.processIPAddress = processIP;
      remote.processPortNumberFull = processPort;
      remote.simulatedIPAddress = simulatedIP;
      synchronized (ports) {
        int slot = freePort();
        if (slot < 0) {
          System.out.println("No free port available after attach was accepted.");
          return;
        }
        ports[slot] = new Link(rd, remote, weight);
      }
    } catch (IOException e) {
      System.out.println("Attach failed: " + e.getMessage());
    }
  }

  private int findPort(String simulatedIP) {
    for (int i = 0; i < ports.length; i++) {
      if (ports[i] != null && ports[i].router2.simulatedIPAddress.equals(simulatedIP)) {
        return i;
      }
    }
    return -1;
  }

  private int freePort() {
    for (int i = 0; i < ports.length; i++) {
      if (ports[i] == null) {
        return i;
      }
    }
    return -1;
  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {
    while (!serverSocket.isClosed()) {
      try {
        final Socket socket = serverSocket.accept();
        Thread handler = new Thread(new Runnable() {
          public void run() {
            handleIncoming(socket);
          }
        }, "router-request");
        handler.setDaemon(true);
        handler.start();
      } catch (IOException e) {
        if (!serverSocket.isClosed()) {
          System.err.println("Unable to accept router request: " + e.getMessage());
        }
      }
    }
  }

  private void handleIncoming(Socket socket) {
    try (Socket connection = socket) {
      connection.setSoTimeout(10000);
      ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
      output.flush();
      ObjectInputStream input = new ObjectInputStream(connection.getInputStream());
      Object message = input.readObject();
      if (!(message instanceof SOSPFPacket)) {
        output.writeBoolean(false);
        output.flush();
        return;
      }
      SOSPFPacket hello = (SOSPFPacket) message;
      if (hello.sospfType != 0 || !rd.simulatedIPAddress.equals(hello.dstIP) ||
          hello.srcIP == null || hello.srcIP.equals(rd.simulatedIPAddress) ||
          hello.srcProcessIP == null || hello.srcProcessPortFull < 1 ||
          hello.srcProcessPortFull > 65535 || hello.linkWeight < 0) {
        output.writeBoolean(false);
        output.flush();
        return;
      }
      if (hello.helloStage == 1) {
        handleStartHello(hello, input, output);
        return;
      }
      if (hello.helloStage != 0) {
        return;
      }
      synchronized (requestLock) {
        synchronized (ports) {
          if (findPort(hello.srcIP) >= 0 || freePort() < 0) {
            output.writeBoolean(false);
            output.flush();
            return;
          }
        }
        AttachRequest request = new AttachRequest();
        // Wait for terminal() to read the user's Y/N answer.
        pendingAttach = request;
        System.out.println("received HELLO from " + hello.srcIP + ";");
        System.out.println("Do you accept this request? (Y/N)");
        try {
          request.answered.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          request.accepted = false;
        }
        if (request.accepted) {
          RouterDescription remote = new RouterDescription();
          remote.processIPAddress = hello.srcProcessIP;
          remote.processPortNumberFull = hello.srcProcessPortFull;
          remote.simulatedIPAddress = hello.srcIP;
          synchronized (ports) {
            if (findPort(hello.srcIP) >= 0 || freePort() < 0) {
              request.accepted = false;
            } else {
              ports[freePort()] = new Link(rd, remote, hello.linkWeight);
            }
          }
        }
        output.writeBoolean(request.accepted);
        output.flush();
      }
    } catch (IOException e) {
      System.err.println("Attach request failed: " + e.getMessage());
    } catch (ClassNotFoundException e) {
      System.err.println("Unknown router request: " + e.getMessage());
    }
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    Link[] attached;
    synchronized (ports) {
      attached = ports.clone();
    }
    for (Link link : attached) {
      if (link == null || link.router2.status == RouterStatus.TWO_WAY) {
        continue;
      }
      RouterDescription neighbor = link.router2;
      try (Socket socket = new Socket(neighbor.processIPAddress, neighbor.processPortNumberFull)) {
        socket.setSoTimeout(10000);
        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
        output.flush();
        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
        output.writeObject(makeHello(neighbor.simulatedIPAddress, 1));
        output.flush();

        Object message = input.readObject();
        if (!(message instanceof SOSPFPacket) ||
            !isHelloFrom((SOSPFPacket) message, neighbor, 2)) {
          System.out.println("Invalid HELLO response from " + neighbor.simulatedIPAddress);
          continue;
        }
        System.out.println("received HELLO from " + neighbor.simulatedIPAddress + ";");
        output.writeObject(makeHello(neighbor.simulatedIPAddress, 3));
        output.flush();
        synchronized (ports) {
          neighbor.status = RouterStatus.TWO_WAY;
        }
        System.out.println("set " + neighbor.simulatedIPAddress + " STATE to TWO_WAY;");
      } catch (IOException e) {
        System.out.println("HELLO exchange failed with " + neighbor.simulatedIPAddress + ": " + e.getMessage());
      } catch (ClassNotFoundException e) {
        System.out.println("Unknown HELLO response from " + neighbor.simulatedIPAddress);
      }
    }
  }

  private SOSPFPacket makeHello(String destinationIP, int stage) {
    SOSPFPacket hello = new SOSPFPacket();
    hello.sospfType = 0;
    hello.helloStage = (short) stage;
    hello.srcProcessIP = rd.processIPAddress;
    hello.srcProcessPortFull = rd.processPortNumberFull;
    hello.srcIP = rd.simulatedIPAddress;
    hello.dstIP = destinationIP;
    return hello;
  }

  private boolean isHelloFrom(SOSPFPacket packet, RouterDescription neighbor, int stage) {
    return packet.sospfType == 0 && packet.helloStage == stage &&
        rd.simulatedIPAddress.equals(packet.dstIP) &&
        neighbor.simulatedIPAddress.equals(packet.srcIP) &&
        neighbor.processIPAddress.equals(packet.srcProcessIP) &&
        neighbor.processPortNumberFull == packet.srcProcessPortFull;
  }

  private void handleStartHello(SOSPFPacket first, ObjectInputStream input,
                                ObjectOutputStream output)
      throws IOException, ClassNotFoundException {
    Link link;
    synchronized (ports) {
      int index = findPort(first.srcIP);
      if (index < 0) {
        return;
      }
      link = ports[index];
      if (!isHelloFrom(first, link.router2, 1)) {
        return;
      }
      System.out.println("received HELLO from " + first.srcIP + ";");
      if (link.router2.status != RouterStatus.TWO_WAY) {
        link.router2.status = RouterStatus.INIT;
        System.out.println("set " + first.srcIP + " STATE to INIT;");
      }
    }

    output.writeObject(makeHello(first.srcIP, 2));
    output.flush();
    Object message = input.readObject();
    if (!(message instanceof SOSPFPacket) ||
        !isHelloFrom((SOSPFPacket) message, link.router2, 3)) {
      return;
    }
    synchronized (ports) {
      System.out.println("received HELLO from " + first.srcIP + ";");
      if (link.router2.status != RouterStatus.TWO_WAY) {
        link.router2.status = RouterStatus.TWO_WAY;
        System.out.println("set " + first.srcIP + " STATE to TWO_WAY;");
      }
    }
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {

    synchronized (ports) {
      for (Link link : ports) {
        if (link != null && link.router2.status == RouterStatus.TWO_WAY) {
          System.out.println(link.router2.simulatedIPAddress);
        }
      }
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short processPort,
                             String simulatedIP, short weight){

  }

  /**
   * update the weight of a specific port.
   * This change should trigger synchronization of the Link State Database by sending 
   * a Link State Advertisement (LSA) update to all neighboring routers in the topology.
   *
   * @param portNumber the port number (0-3) to update
   * @param newWeight the new weight/cost for the link attached to this port
   */
  private void processUpdate(short portNumber, short newWeight) {

  }

  /**
   * send an application-level message from this router to the destination router.
   * The message must be forwarded hop-by-hop according to the current shortest path.
   * <p/>
   * When you run send, the window of the router where you run the command should print:
   * "Sending message to <Destination IP>"
   * <p/>
   * For each intermediate router on the shortest path (excluding the source and destination), 
   * the router window should print:
   * "Forwarding packet from <Source IP> to <Destination IP>"
   * <p/>
   * When the destination router receives the message, the router window should print:
   * "Received message from <Source IP>:"
   * "<Message>"
   *
   * @param destinationIP the simulated IP address of the destination router
   * @param message the message content to send
   */
  private void processSend(String destinationIP, String message) {

  }

  /**
   * handle incoming application message packet.
   * This method should be called when a router receives a SOSPFPacket with sospfType = 2 (Application Message).
   * <p/>
   * If this router is the destination (packet.dstIP equals this router's simulatedIPAddress):
   * - Print "Received message from <Source IP>:"
   * - Print the message content
   * <p/>
   * If this router is an intermediate router:
   * - Print "Forwarding packet from <Source IP> to <Destination IP>"
   * - Forward the packet to the next hop on the shortest path to the destination
   * - Do NOT print or inspect the message payload
   *
   * @param packet the received application message packet
   */
  private void handleApplicationMessage(socs.network.message.SOSPFPacket packet) {

  }

  // Handle PA1 input before the original command dispatch to support full port numbers.
  private boolean handlePA1Input(String command) {
    AttachRequest request = pendingAttach;
    if (request != null) {
      if (command.equalsIgnoreCase("Y") || command.equalsIgnoreCase("N")) {
        request.accepted = command.equalsIgnoreCase("Y");
        if (!request.accepted) {
          System.out.println("You rejected the attach request;");
        }
        pendingAttach = null;
        request.answered.countDown();
      } else {
        System.out.println("Please answer Y or N.");
      }
      return true;
    }
    if (command.equals("attach") || command.startsWith("attach ")) {
      String[] cmdLine = command.trim().split("\\s+");
      if (cmdLine.length != 5) {
        System.out.println("Usage: attach [Process IP] [Process Port] [IP Address] [Link Weight]");
        return true;
      }
      try {
        processAttach(cmdLine[1], Integer.parseInt(cmdLine[2]),
                cmdLine[3], Short.parseShort(cmdLine[4]));
      } catch (NumberFormatException e) {
        System.out.println("Invalid attach port or link weight.");
      }
      return true;
    }
    return false;
  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command == null) {
          break;
        }
        if (handlePA1Input(command)) {
          System.out.print(">> ");
          command = br.readLine();
          continue;
        }
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else if (command.startsWith("send ")) {
          //send [Destination IP] [Message]
          String[] cmdLine = command.split(" ", 3);
          if (cmdLine.length >= 3) {
            processSend(cmdLine[1], cmdLine[2]);
          } else {
            System.out.println("Usage: send [Destination IP] [Message]");
          }
        } else if (command.startsWith("update ")) {
          //update [port_number] [new_weight]
          String[] cmdLine = command.split(" ");
          if (cmdLine.length >= 3) {
            processUpdate(Short.parseShort(cmdLine[1]), Short.parseShort(cmdLine[2]));
          } else {
            System.out.println("Usage: update [port_number] [new_weight]");
          }
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
