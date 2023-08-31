package Src;

import java.io.IOException;
import java.net.*;
import java.util.List;

public class ThreadMultiCast implements Runnable{
    
    private String message = "";
    private MulticastSocket MULTICAST_SOCKET;
    private static List<String> LIST_MESSAGE; // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private boolean stop;

    public ThreadMultiCast(MulticastSocket mc_socket, List<String> list_message ){
        MULTICAST_SOCKET = mc_socket;
        LIST_MESSAGE = list_message;
        stop = false;
    }

    @Override
    public void run() {
        //in attesa infinita di messaggi provenienti da qualsisi client se invia messaggi
        ps("\nUTENTE CONNESSO AL GRUPPO SOCIALE PER LA CONDIVISIONE DEI RISULTATI ...\n");
        while (!stop){
            try {
                this.receiveMessage();
            } catch (IOException e) {
                //nascondo il messaggio di errore di chiusura socket
                return;
            }
        }
    }

   //metodo per ricevere un messaggio dal socket del multicast, in attesa continua
    public void receiveMessage() throws IOException{
        //pacchetto dove ricevere risposta dal client - creo pacchetto con dimensione buffer e buffer
        DatagramPacket receive = new DatagramPacket(new byte[1024], 1024);
        MULTICAST_SOCKET.receive(receive);
        //mi metto in attesa di ricevere il messaggio e salvarlo nel buffer packet
        message = new String(receive.getData(),0,receive.getLength());
        LIST_MESSAGE.add(message);
        //aggiunto messsggio ricevuto (riguarda i suggerimenti di un round) nella lista dei messaggi
    }

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    //metodo per far terminare il thread del multicast
    public void stopThread(){
        stop = true;
    }
}

