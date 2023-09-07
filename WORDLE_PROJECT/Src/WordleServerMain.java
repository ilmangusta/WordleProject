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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class WordleServerMain{

    //1. server gestisce accesso login o sign in. Risponde con avvenuta registrazione o se errore password o username gia presente. Ricorrere ad un database per i dati.
    //2. Server risponde con messaggi anche per login (username o password errati).
    //3. Server deve estratte giornalmente una SW Secret Word da indovinare
    //4. Se utente ha gia indovinato la parola fallisce
    //5. Server risponde indicando se la parola è presente nel vocabolario e, in questo caso,fornendo gli indizi. Se la parola non è presente, viene inviato un codice particolare,però il tentativo non viene contato tra i 12 tentativi consentiti.

    //importing from config
    private static int SERVER_SOCKET_PORT; //porta per connessione socket 
    private static int MULTICAST_PORT; //porta per connessione socket 
    private static String MULTICAST_ADDRESS; //indirizzo per connessione socket 
    private static long PERIOD; //periodo in minuti tra la pubblicazione di una parola e la prossima
    private static long TIMEOUT_SERVER; //periodo di timeout da aspettare prima di chiamare una shotdown now         
    private static String FILE_NAME_WORDS; //nome del file delle parole segrete
    private static String FILE_NAME_DB; //nome del file json con info giocatori

    private Socket SOCKET; //socket che si crea una volta accettata la connessione con un client
    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private ServerSocket SERVER_SOCKET; //socket che sarà utilizzato per la conunicazione TCP
    private InetAddress MULTICAST_GROUP; // indirizzo per la comunicazione
    private static AtomicInteger GAME_ID = new AtomicInteger(0); //id del gioco/round attuale di wordle
    private int PLAYER_ID; //id del giocatore connesso
    private static Database DB; //personale database per tenere i dati dei giocatori
    private static List<String> UTENTI_CONNESSI = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare chi è connesso
    private static List<String> SECRET_WORDS = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private static List<Giocatore> STATS_GIOCATORI; //una lista di giocatori per le statistiche, utilizzata per l'aggiornamento dei dati
    private static ConcurrentHashMap<String,String> UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema
    //utilizzo un cached thread pool standard cosi se un thread rimane inutilizzato per 60 secondi, la sua esecuzione termina anche se è non capita.
    private ExecutorService cachedPool = Executors.newCachedThreadPool();

    //costruttore wordleservermain instaura connessione socket nella porta selezionata
    public WordleServerMain() throws IOException{
        try {
            SERVER_SOCKET = new ServerSocket(SERVER_SOCKET_PORT);
        } catch (IOException e) {
            System.err.printf("ERROR constructor servermain: "+ e.getMessage());
            System.exit(1);
        }
    }

    //metodo che avvia attivita del server
    public void startServer() throws IOException{
        PLAYER_ID = 0;
        //utilizzo questo handler per catturare il segnale ctrl + c  o quando viene invocato il System.exit() che fa terminare il server
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    ps("\nCHIUSURA FORZATA SERVER ... SHUTTING DOWN ..."); 
                    ps("AGGIORNO STATISTICHE NEL JSON");
                    DB.updateJson();
                    try {SERVER_SOCKET.close();}
                    catch (IOException e) {
                        System.err.print("ERROR socket close\n");
                        e.printStackTrace();
                    }     
                    cachedPool.shutdown();
                    try {
                        if (!cachedPool.awaitTermination(TIMEOUT_SERVER, TimeUnit.SECONDS)){
                            cachedPool.shutdownNow();
                        }
                    }
                    catch (InterruptedException ex) {cachedPool.shutdownNow();}
                    cachedPool.shutdownNow();
                    closeMultiCast();
                    //TERMINAZIONE PROCESSO SERVER TUTTO OK 
                    ps("... SERVER TERMINATO!.");
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        try {
            ps("WAITING FOR CONNECTION CLIENTS...");
            while(true){
                SOCKET = this.SERVER_SOCKET.accept();
                //richiesta connessione accettata->inizia gioco 
                PLAYER_ID++;
                ps("PLAYER: "+PLAYER_ID+" CONNECTED!");
                //avvio l'esecuzione di un threadServer nel cachedpool personalizzato
                cachedPool.execute(new ThreadServer(SOCKET, MULTICAST_SOCKET, MULTICAST_PORT, MULTICAST_GROUP, PLAYER_ID, GAME_ID, STATS_GIOCATORI, UTENTI_REGISTRATI, UTENTI_CONNESSI, SECRET_WORDS, FILE_NAME_WORDS));
            }
        } catch (Exception e) {
            System.err.printf("ERROR server: "+ e.getMessage());
            System.exit(1);
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
                    int fileElement = ((int) file.length()) / 11;
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
                        ps("ERROR startselectword: "+ e.getMessage());
                    }     
                    GAME_ID.incrementAndGet();
                    //line = SECRET_WORD del round
                    SECRET_WORDS.add(line);
                    ps("NUOVA PAROLA DELLA GIORNATA: "+ line+" - GAME_ID: "+GAME_ID);
                    try {
                        br.close();
                    } catch (Exception e) {
                        System.err.printf("ERROR startselectword closing file\n");
                        System.exit(1);                    
                    }
                } catch (IOException e) {
                    System.err.printf("ERROR processingfile: "+ e.getMessage());
                    System.exit(1);
                }
            }
        },0, PERIOD * 1000);
    }

    //metodo che permette di inizializzare le strutture dati utilizzate
    public void setupServer() throws IOException{ 
        STATS_GIOCATORI = DB.STATS_GIOCATORI;
        UTENTI_REGISTRATI = DB.UTENTI_REGISTRATI;
    }

    //metodo per instaurare il gruppo multicast utilizzato per la condivisione sociale
    public void setupMultiCast() throws IOException{
        try{
            //imposto i parametri da passare ai thread per il gruppo sociale multicast
            MULTICAST_SOCKET = new MulticastSocket(MULTICAST_PORT);
            MULTICAST_GROUP = InetAddress.getByName(MULTICAST_ADDRESS);
            MULTICAST_SOCKET.joinGroup(MULTICAST_GROUP); //deprecato
        } catch (SocketException e) {
            System.err.printf("ERROR setupmulticast: "+ e.getMessage());
            System.exit(1);  
        }
    }

    // metodo per lasciare il gruppo multicast e chiuderlo
    public void closeMultiCast(){
        if (MULTICAST_SOCKET!=null ){
            if ( !MULTICAST_SOCKET.isClosed()){
                try{
                    MULTICAST_SOCKET.leaveGroup(MULTICAST_GROUP);
                    MULTICAST_SOCKET.close();
                }catch(Exception e){
                    System.err.printf("\nERROR closemulticast\n");
                }
                ps("\nABBANDONO LA PIATTAFORMA SOCIALE PER LA CONDIVISIONE ...");
            }
        }
        //else{ps("NESSUN GRUPPO DA CHIUDERE");}
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
        TIMEOUT_SERVER = Integer.parseInt(props.getProperty("timeout"));
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
            SERVER.closeMultiCast();
        }catch(Exception e){
            System.err.printf("ERROR servermain: "+ e.getMessage());
            System.exit(1);        
        }
    }
}
