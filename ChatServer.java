import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static Map<String, Set<ClientHandler>> chatRooms = new HashMap<>();
    private static Set<ClientHandler> onlineUsers = new HashSet<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Chat server started on port 12345...");

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                onlineUsers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void joinRoom(String roomName, ClientHandler clientHandler) {
        chatRooms.putIfAbsent(roomName, new HashSet<>());
        chatRooms.get(roomName).add(clientHandler);
    }

    public static synchronized void leaveRoom(String roomName, ClientHandler clientHandler) {
        if (chatRooms.containsKey(roomName)) {
            chatRooms.get(roomName).remove(clientHandler);
            if (chatRooms.get(roomName).isEmpty()) {
                chatRooms.remove(roomName);
            }
        }
    }

    public static synchronized void broadcastMessage(String roomName, String message, ClientHandler sender) {
        if (chatRooms.containsKey(roomName)) {
            for (ClientHandler client : chatRooms.get(roomName)) {
                if (client != sender) {
                    client.sendMessage(message);
                }
            }
        }
    }

    public static synchronized Set<ClientHandler> getOnlineUsers() {
        return onlineUsers;
    }

    public static synchronized void removeOnlineUser(ClientHandler clientHandler) {
        onlineUsers.remove(clientHandler);
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String currentRoom;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            out.println("Enter your username:");
            String username = in.readLine();
            out.println("Welcome " + username + "! You can join or create a room by typing: /join roomName");

            while (true) {
                String message = in.readLine();
                if (message.startsWith("/join ")) {
                    String roomName = message.substring(6);
                    if (currentRoom != null) {
                        ChatServer.leaveRoom(currentRoom, this);
                    }
                    currentRoom = roomName;
                    ChatServer.joinRoom(roomName, this);
                    out.println("Joined room: " + roomName);
                } else if (message.startsWith("/online")) {
                    out.println("Online users:");
                    for (ClientHandler user : ChatServer.getOnlineUsers()) {
                        out.println(user);
                    }
                } else if (currentRoom != null) {
                    ChatServer.broadcastMessage(currentRoom, username + ": " + message, this);
                } else {
                    out.println("You need to join a room first. Use: /join roomName");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (currentRoom != null) {
                    ChatServer.leaveRoom(currentRoom, this);
                }
                ChatServer.removeOnlineUser(this);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}
