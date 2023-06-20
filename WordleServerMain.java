//package ProjectLab;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class WordleServerMain{

    //1. server gestisce accesso login o sign in. Risponde con avvenuta registrazione o se errore password o username gia presente. Ricorrere ad un database per i dati.
    //2. Server risponde con messaggi anche per login (username o password errati).
    //3. Server deve estratte giornalmente una SW Secret Word da indovinare
    //4. Se utente ha gia indovinato la parola fallisce
    //5. Server risponde indicando se la parola è presente nel vocabolario e, in questo caso,fornendo gli indizi. Se la parola non è presente, viene inviato un codice particolare,però il tentativo non viene contato tra i 12 tentativi consentiti.
  
    private static int SERVER_SOCKET_PORT; //porta per connessione socket 
    private int PLAYER_ID=0; //id del giocatore connesso
    private String SECRET_WORD; //parola da indovinare aggiornata frequentemente
    private static String FILE_NAME_WORDS; //nome del file delle parole segrete
    private static String FILE_NAME_DB; //nome del file json con info giocatori

    private ServerSocket SERVER_SOCKET; //socket che sarà utilizzato per la conunicazione
    private static Database DB; //personale database per tenere i dati dei giocatori
    private static ArrayList<Giocatore> STATS_GIOCATORI; //una lista di giocatori, utilizzata per l'aggiornamento dei dati
    private static ConcurrentHashMap<String,String> UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema
    private static ConcurrentHashMap<String,ArrayList<String>> PAROLE_GIOCATE; // una hashmap con coppia Giocatore - Tutte le parole giocate
    private static List<String> UTENTI_CONNESSI = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare chi è connesso

    //costruttore wordleservermain instaura connessione socket nella porta selezionata
    public WordleServerMain() throws IOException{
        try {
            SERVER_SOCKET=new ServerSocket(SERVER_SOCKET_PORT);
        } catch (IOException e) {
            ps("Error IO [32]: "+e.getMessage());
        }
    }

    //metodo che permette di inizializzare le strutture dati utilizzate
    public void setupServer() throws IOException{ 
        STATS_GIOCATORI=DB.STATS_GIOCATORI;
        UTENTI_REGISTRATI=DB.UTENTI_REGISTRATI;
        PAROLE_GIOCATE=DB.PAROLE_GIOCATE;
    }

    //metodo che avvia attivita del server
    public void startServer() throws IOException{
        ps("WAITING FOR CONNECTION CLIENTS...");
        while(true){
            Socket SOCKET=this.SERVER_SOCKET.accept();
            //richiesta connessione accettata->inizia gioco 
            PLAYER_ID++;
            ps("PLAYER: "+PLAYER_ID+" CONNECTED!");
            //id che invio al client    
            Thread th=new Thread(new ThreadServer(SOCKET, PLAYER_ID, STATS_GIOCATORI, PAROLE_GIOCATE, UTENTI_REGISTRATI, UTENTI_CONNESSI, SECRET_WORD, FILE_NAME_WORDS));
            th.start();
        }
    }

    //metodo per avviare un thread che si occupi di aggiornare la secretWord periodicamente
    public void startSelectWord(){
        Thread th=new Thread(new ThreadSelectWord());
        th.start();
    }

    //scegli la parola giornaliera
    public void selectWord() throws IOException{

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

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    public static void main(String[] args) {
        
        ps("------- SERVER SIDE ---------");
        try {
            //configuration file per ottenere tutte le informazioni utili come parametri o valori defualt 
            Properties props = new Properties();
            InputStream in = new FileInputStream("ConfigServer.properties");
            props.load(in);
            FILE_NAME_WORDS=props.getProperty("file_name_words");
            FILE_NAME_DB=props.getProperty("file_name_db");
            SERVER_SOCKET_PORT=Integer.parseInt(props.getProperty("port"));
            in.close();
            WordleServerMain  SERVER=new WordleServerMain();
            ps("Setting all server stuffs...");
            DB=new Database(FILE_NAME_DB);
            SERVER.setupServer();
            SERVER.selectWord();
            //SERVER.startselectWord();
            SERVER.startServer();
        }catch(Exception e){
            ps("Error configuration server: "+e.getMessage());
        }
    }
}
