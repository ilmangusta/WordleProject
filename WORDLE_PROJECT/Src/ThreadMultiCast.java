package Src;

import java.net.*;
import java.util.List;

public class ThreadMultiCast extends Thread{
    
    private String message = "";
    private MulticastSocket MULTICAST_SOCKET;
    private List<String> LIST_MESSAGE; // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private boolean stop;
    private int BUFF_LENGTH; 

    public ThreadMultiCast(MulticastSocket mc_socket, List<String> list_message, int buff_length){
        this.MULTICAST_SOCKET = mc_socket;
        this.LIST_MESSAGE = list_message;
        this.stop = false;
        this.BUFF_LENGTH = buff_length;
    }

    //devo effettuare override del run()
    @Override
    public void run() {
        //in attesa infinita di messaggi provenienti da qualsisi client se invia messaggi
        ps("\nUTENTE CONNESSO AL GRUPPO SOCIALE PER LA CONDIVISIONE DEI RISULTATI ...");
        while (!this.stop){
            this.receiveMessage();
        }
        //ps("THREAD MULTICAST INTERROTTO");
    }

   //metodo per ricevere un messaggio dal socket del multicast, in attesa continua
    public void receiveMessage(){
        //pacchetto dove ricevere risposta dal client - creo pacchetto con dimensione buffer e buffer
        DatagramPacket receive = new DatagramPacket(new byte[BUFF_LENGTH], BUFF_LENGTH);
        try {
            MULTICAST_SOCKET.receive(receive);
        } catch (Exception e) {
            //System.err.println("ERROR receive package");
            //nascondo il messaggio di errore che mi da quando abbandono ma sta aspettando messaggio
            return;
        }
        //mi metto in attesa di ricevere il messaggio e salvarlo nel buffer packet
        message = new String(receive.getData(),0,receive.getLength());
        this.LIST_MESSAGE.add(message);
        //aggiunto messsaggio ricevuto (riguarda i suggerimenti di un round) nella lista dei messaggi
    }

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    //metodo per far terminare il thread del multicast. utilizzo un metodo sychronized per assicurarmi la sincronizzazione
    public synchronized void stopThread(){
        this.stop = true;
    }
}

