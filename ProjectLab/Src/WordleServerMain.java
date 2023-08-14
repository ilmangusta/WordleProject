package Src;

//package Src;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class WordleServerMain{

    //1. server gestisce accesso login o sign in. Risponde con avvenuta registrazione o se errore password o username gia presente. Ricorrere ad un database per i dati.
    //2. Server risponde con messaggi anche per login (username o password errati).
    //3. Server deve estratte giornalmente una SW Secret Word da indovinare
    //4. Se utente ha gia indovinato la parola fallisce
    //5. Server risponde indicando se la parola è presente nel vocabolario e, in questo caso,fornendo gli indizi. Se la parola non è presente, viene inviato un codice particolare,però il tentativo non viene contato tra i 12 tentativi consentiti.
  
    private static int SERVER_SOCKET_PORT; //porta per connessione socket 
    private static int MULTICAST_PORT; //porta per connessione socket 
    private static String MULTICAST_ADDRESS; //indirizzo per connessione socket 
    private InetAddress MULTICAST_GROUP; // indirizzo per la comunicazione
    private static int PERIOD; //periodo in minuti tra la pubblicazione di una parola e la prossima    
    private int PLAYER_ID = 0; //id del giocatore connesso
    private static AtomicInteger GAME_ID = new AtomicInteger(0); //id del gioco/round attuale di wordle
    private volatile String SECRET_WORD; //parola da indovinare aggiornata frequentemente
    
    private static String FILE_NAME_WORDS; //nome del file delle parole segrete
    private static String FILE_NAME_DB; //nome del file json con info giocatori

    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private ServerSocket SERVER_SOCKET; //socket che sarà utilizzato per la conunicazione TCP
    private Socket SOCKET; //socket che si crea una volta accettata la connessione con un client
    private static Database DB; //personale database per tenere i dati dei giocatori
    private static List<String> SECRET_WORDS = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private static ArrayList<Giocatore> STATS_GIOCATORI; //una lista di giocatori per le statistiche, utilizzata per l'aggiornamento dei dati
    private static ConcurrentHashMap<String,String> UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema
    private static ConcurrentHashMap<String,ArrayList<String>> PAROLE_GIOCATE; // una hashmap con coppia Giocatore - Tutte le parole giocate
    private static List<String> UTENTI_CONNESSI = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare chi è connesso

    //costruttore wordleservermain instaura connessione socket nella porta selezionata
    public WordleServerMain() throws IOException{
        try {
            SERVER_SOCKET = new ServerSocket(SERVER_SOCKET_PORT);
        } catch (IOException e) {
            ps("ERROR servermain: "+e.getMessage());
        }
    }

    //metodo che avvia attivita del server
    public void startServer() throws IOException{
        ps("WAITING FOR CONNECTION CLIENTS...");
        while(true){
            SOCKET = this.SERVER_SOCKET.accept();
            //richiesta connessione accettata->inizia gioco 
            PLAYER_ID++;
            ps("PLAYER: "+PLAYER_ID+" CONNECTED!");
            //faccio partire il thread che si occupa della sessione di gioco di un utente specifico   
            Thread server_thread = new Thread(new ThreadServer(SOCKET, MULTICAST_SOCKET, MULTICAST_PORT, MULTICAST_GROUP, PLAYER_ID, GAME_ID, STATS_GIOCATORI, PAROLE_GIOCATE, UTENTI_REGISTRATI, UTENTI_CONNESSI, SECRET_WORD, SECRET_WORDS, FILE_NAME_WORDS, FILE_NAME_DB));
            server_thread.start();
        }
    }

    
    //metodo per avviare un timer che si occupi di aggiornare la secretWord periodicamente
    public void startSelectWord() {
        //aspetta il timer e modifica password
        Timer timer = new Timer();

        timer.schedule(new TimerTask(){

            public void run(){
                //Orario nuova parola arrivato -> Scelgo nuova parola
                File file = new File(FILE_NAME_WORDS);
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    int fileElement = ((int) file.length()) / 10;
                    int cont = 0;
                    int element = (int)((Math.random()*fileElement-1)+1);
                    String line = "";
                    try {
                        while (true){
                            if(cont >= element){
                                break;
                            }
                            line = br.readLine();
                            cont++;
                        }
                    } catch (IOException e) {
                        ps("ERROR startselectword: "+e.getMessage());
                    }     
                    GAME_ID.incrementAndGet();
                    SECRET_WORD = line;
                    SECRET_WORDS.add(SECRET_WORD);
                    ps("NUOVA PAROLA DELLA GIORNATA: "+ line+" - GAME_ID: "+GAME_ID);
                    try {
                        br.close();
                    } catch (Exception e) {
                        System.err.printf("ERROR startselectword closing file: "+e.getMessage());
                        System.exit(1);                    
                    }
                } catch (IOException e) {
                    System.err.printf("ERROR processingfile: "+e.getMessage());
                    System.exit(1);
                }
            }
        },0,PERIOD*60*1000);
    }

    //metodo che permette di inizializzare le strutture dati utilizzate
    public void setupServer() throws IOException{ 
        STATS_GIOCATORI = DB.STATS_GIOCATORI;
        UTENTI_REGISTRATI = DB.UTENTI_REGISTRATI;
        PAROLE_GIOCATE = DB.PAROLE_GIOCATE;
    }

    //metodo per instaurare il gruppo multicast utilizzato per la condivisione sociale
    public void setupMultiCast() throws IOException{
        try{
            MULTICAST_SOCKET = new MulticastSocket(MULTICAST_PORT);
            MULTICAST_GROUP = InetAddress.getByName(MULTICAST_ADDRESS);
            MULTICAST_SOCKET.joinGroup(MULTICAST_GROUP); //deprecato
            //Thread multicast_thread = new Thread(new ThreadMultiCast("Server", MULTICAST_SOCKET));
            //multicast_thread.start();
        } catch (SocketException e) {
            System.err.printf("ERROR setupmulticast: "+e.getMessage());
            System.exit(1);  
        }
    }

    //metodo per leggere i parametri dei metodi
	public static void readConfig() throws FileNotFoundException, IOException {
        ps("------- PARAMETERS SETTINGS  ---------");
        //configuration file per ottenere tutte le informazioni utili come parametri o valori defualt 
		InputStream in = new FileInputStream("Config/Server.properties");
        Properties props = new Properties();
        props.load(in);
        FILE_NAME_WORDS = props.getProperty("file_name_words");
        FILE_NAME_DB = props.getProperty("file_name_db");
        SERVER_SOCKET_PORT = Integer.parseInt(props.getProperty("port"));
        MULTICAST_ADDRESS = props.getProperty("multicast_socket_address");
        MULTICAST_PORT = Integer.parseInt(props.getProperty("multicast_socket_port"));
        PERIOD = Integer.parseInt(props.getProperty("period"));
        in.close();
	}

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {

        ps("------- SERVER SIDE ---------");
        try {
            readConfig();
            WordleServerMain  SERVER = new WordleServerMain();
            ps("------- DATABASE SETTINGS ---------");
            DB = new Database(FILE_NAME_DB, GAME_ID);
            ps("------- SERVER SETTINGS ---------");
            SERVER.startSelectWord();
            SERVER.setupServer();
            SERVER.setupMultiCast();
            SERVER.startServer();
        }catch(Exception e){
            System.err.printf("ERROR servermain: "+e.getMessage());
            System.exit(1);        
        }
    }
}
