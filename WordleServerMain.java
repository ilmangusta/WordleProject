//package ProjectLab;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class WordleServerMain{

    //server gestisce accesso login o sign in. Risponde con avvenuta registrazione
    //o se errore password o username gia presente. Ricorrere ad un database per i dati.
    //Server risponde con messaggi anche per login (username o password errati).
    //Server deve estratte giornalmente una SW Secret Word da indovinare
    //Se utente ha gia indovinato la parola fallisce
    //Server risponde indicando se la parola è presente nel vocabolario e, in questo caso, 
    //fornendo gli indizi. Se la parola non è presente, viene inviato un codice particolare, 
    //però il tentativo non viene contato tra i 12 tentativi consentiti.
  
    private ServerSocket SERVER_SOCKET;
    private int NUM_PLAYERS=0;
    private String SECRET_WORD;
    private String FILE_NAME_WORDS="./words.txt";
    
    private static ArrayList<Giocatore> STATS_GIOCATORI;
    private static ConcurrentHashMap<String,String> UTENTI_REGISTRATI;
    private static ConcurrentHashMap<String,ArrayList<String>> PAROLE_GIOCATE;
    private static List<String> UTENTI_CONNESSI = Collections.synchronizedList(new ArrayList<String>());
    

    public WordleServerMain() throws IOException{
        try {
            this.SERVER_SOCKET=new ServerSocket(10501);
        } catch (IOException e) {
            ps("Error IO [32]: "+e.getMessage());
        }
    }

    //metodo che avvia attivita del server
    public void startServer() throws IOException{
        System.out.println("WAITING FOR CONNECTION CLIENTS...");
        while(true){
            Socket SOCKET=this.SERVER_SOCKET.accept();
            //richiesta connessione accettata->inizia gioco 
            NUM_PLAYERS++;
            ps("PLAYER: "+NUM_PLAYERS+" CONNECTED!");
            //id che invio al client    
            Thread th=new Thread(new ThreadServer(SOCKET, NUM_PLAYERS, STATS_GIOCATORI, PAROLE_GIOCATE, UTENTI_REGISTRATI, UTENTI_CONNESSI, SECRET_WORD));
            th.start();
        }
    }

    //metodo per avviare un thread che si occupi di aggiornare la secretWord periodicamente
    public void startScegliParola(){
        Thread th=new Thread(new ThreadScegliParola());
        th.start();
    }

    //scegli la parola giornaliera
    public void scegliParola() throws IOException{
        File file = new File( FILE_NAME_WORDS);
        BufferedReader br = new BufferedReader(new FileReader(file));
        int fileElement=((int) file.length()) / 11;
        int cont=0;
        int element=(int)((Math.random()*fileElement-1)+1);
        try {
            while (true){
                if(cont>=element){
                    break;
                }
                SECRET_WORD = br.readLine();
                cont++;
            }
        } catch (IOException e) {
            ps("Error IO [151]: "+e.getMessage());
        }     
        ps("NUOVA PAROLA DELLA GIORNATA: "+ SECRET_WORD);
        try {
            br.close();
        } catch (Exception e) {
            ps("Exception error closing file [157]: "+e.getMessage());
        }
    }

    public static void ps(String s){
        System.out.println(s);
    }

    public static void main(String[] args) throws IOException{
        
        ps("------- SERVER SIDE ---------");
        WordleServerMain  SERVER=new WordleServerMain();        
        Database DB=new Database();
        STATS_GIOCATORI=DB.STATS_GIOCATORI;
        UTENTI_REGISTRATI=DB.UTENTI_REGISTRATI;
        PAROLE_GIOCATE=DB.PAROLE_GIOCATE;
        SERVER.scegliParola();
        //server.startScegliParola();
        SERVER.startServer();
    }
}
