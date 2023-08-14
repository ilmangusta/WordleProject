package Src;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreadMultiCast implements Runnable{
    
    private String process;
    private String message = "";
    private MulticastSocket MULTICAST_SOCKET;
    private static List<String> LIST_MESSAGE = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte

    public ThreadMultiCast(String mode, MulticastSocket mc_socket, List<String> list_message ){
        process = mode; 
        MULTICAST_SOCKET = mc_socket;
        LIST_MESSAGE = list_message;
    }

    //metodo per ricevere un messaggio dal socket del multicast, in attesa continua
    public void receiveMessage() throws IOException{
        //pacchetto dove ricevere risposta dal client
        DatagramPacket receive = new DatagramPacket(new byte[1024], 1024);
        MULTICAST_SOCKET.receive(receive);
        message = new String(receive.getData(),0,receive.getLength());
        LIST_MESSAGE.add(message);
    }

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    @Override
    public void run() {
        //in attesa infinita di messaggi provenienti da qualsisi client se invia messaggi
        ps("\nMULTICAST UTENTI: "+process+" connected ...");
        ps("CLIENT in attesa di ricevere un messaggio dalla piattaforma social ...\n");
        while (true){
            try {
                this.receiveMessage();
            } catch (IOException e) {
                //nascondo il messaggio di errore di chiusura socket
                return;
            }
        }
    }
}

