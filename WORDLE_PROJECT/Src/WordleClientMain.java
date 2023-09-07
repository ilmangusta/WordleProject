package Src;

//package Src;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class WordleClientMain {
    
    //import from config
    private static int SOCKET_PORT;
    private static String SOCKET_ADDRESS; //indirizzo per connessione con socket TCP
    private static int MULTICAST_PORT; //porta per connessione socket 
    private static String MULTICAST_ADDRESS; //indirizzo per connessione socket multicast per condivisione social 
    private static int BUFF_LENGTH; //lunghezza del buffer per i pacchetti del multicast

    private Socket SOCKET;
    private BufferedReader dataIn;
    private PrintWriter dataOut;
    public String PLAYER_ID;
    private int TENTATIVO;
    private static Scanner stdin;
    private static String USERNAME;
    private static String PASSWORD;
    private InetAddress MULTICAST_GROUP;
    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private String shareInfo = "";
    private String suggerimenti = "";
    private boolean running = true;
    private static List<String> LIST_MESSAGE = Collections.synchronizedList(new ArrayList<String>());; // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private ThreadMultiCast MULTI_CAST_THREAD;
    
    // costruttore wordleclientmain inizializzo canale socket 
    public WordleClientMain(){
        stdin = new Scanner(System.in);
        try {        
            SOCKET = new Socket(SOCKET_ADDRESS, SOCKET_PORT);
            ps("CONNESSIONE AVVENUTA CON SUCCESSO"); 
            this.createCommunication();
        } catch (IOException e) {
            System.err.printf("\nERROR client main\n");
        }
    }

    // inizializzo canali per la comunicazione
    public void createCommunication(){
        try{
            //dataIn = new DataInputStream((socket.getInputStream()));
            //dataOut = new DataOutputStream((socket.getOutputStream()));
            //dataIn = (socket.getInputStream());
            //dataOut = (socket.getOutputStream());
            dataIn = new BufferedReader(new InputStreamReader(SOCKET.getInputStream()));
            //dataIn = new Scanner(SOCKET.getInputStream());
            dataOut = new PrintWriter(SOCKET.getOutputStream(),true);
            PLAYER_ID = this.Receive();
        } catch (IOException e) {
            System.err.printf("\nERROR communication\n");
            System.exit(1);        
        }
    }

    // utilizzato per chiudere i canali di comunicazione
    public void closeConnection(){
        try {
            if(!SOCKET.isClosed() & SOCKET != null){
                dataIn.close();
                dataOut.close(); 
                SOCKET.close();
            }
        } catch (Exception e) {
            System.err.printf("\nERROR close connection\n");
        }
    }

    //metodo per instaurare il gruppo multicast utilizzato per la condivisione sociale
    public void setupMultiCast() {
        try{
            MULTICAST_SOCKET = new MulticastSocket(MULTICAST_PORT);
            MULTICAST_GROUP = InetAddress.getByName(MULTICAST_ADDRESS);
            MULTICAST_SOCKET.joinGroup(MULTICAST_GROUP); //deprecato
            //faccio partire il thread multicast che si occupa di ricevere in attesa i messaggi
            MULTI_CAST_THREAD = new ThreadMultiCast(MULTICAST_SOCKET, LIST_MESSAGE, BUFF_LENGTH);
            MULTI_CAST_THREAD.start();
        } catch (Exception e) {
            System.err.printf("\nERROR setupmulticast\n");
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
                //CRASHATO UTENTE ALLORA ABBANDONO SESSIONE 
                MULTI_CAST_THREAD.stopThread();
                ps("ABBANDONO LA PIATTAFORMA SOCIALE PER LA CONDIVISIONE ...");
            }
        }
        //else{ps("NESSUN GRUPPO DA CHIUDERE");}
    }

    //metodo per iniziare ad inserire stringhe ma il gioco non è ancora iniziato
    public void startClient(){
        String mode;
        int n;
        while(running){
            //gestisco il segnale se arriva ctr+c allora termino il sistema
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    Send("CRASH_ERROR_CODE");
                    if (running){
                        ps("\nCRASH DEL SISTEMA...\nRIPROVA PIU TARDI ...\n");
                        running = false;
                        closeConnection();
                        closeMultiCast();
                    }
                }
            });

            ps("\nBenvenuto in Wordle!\nSeleziona una opzione:\n\n1. [REGISTRAZIONE]\n2. [ACCESSO]\n3. [CHIUDI TERMINALE]\n");
            mode = ReceiveInput();
            if(mode.toLowerCase().contentEquals("registrazione") || mode.toLowerCase().contentEquals("1")){       
                this.register();
            }else if(mode.toLowerCase().contentEquals("accesso") || mode.toLowerCase().contentEquals("2")){
                if(this.login() == 1){
                    //se login effettuato con successo mi unisco al gruppo multicast
                    this.setupMultiCast();
                    LIST_MESSAGE.clear();
                    shareInfo = "";
                    n = this.startSession();
                    if (n == -1){
                        return;
                    }
                }
            }else if(mode.toLowerCase().contentEquals("chiudi") || mode.toLowerCase().contentEquals("3")){
                this.Send("CHIUDITERMINALE");
                running = false;
                break;

            }else{
                ps("\nOPZIONE ERRATA, RIPROVA.");
            }
        }
        this.closeMultiCast();
        this.closeConnection();
        //ps("\nCONNESSIONE CLIENT TERMINATA ... CHIUSURA ...");
    }

    //inizia la sessione dell'utente il quale può effettuare diverse operazioni
    public int startSession(){  
        String line;
        while(running){
            ps("\nInserisci nome comando o numero.\n\n1. [GIOCA]: Inizia un nuovo round di wordle;\n2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;\n3. [CONDIVIDI]: Condividi i risultati del gioco sul social;\n4. [NOTIFICHE]: Mostra notifiche di gioco inviate dai giocatori;\n5. [ESCI]: Termina la sessione di gioco\n6. [HELP PER LEGENDA].");
            line = ReceiveInput();
            if(line.toLowerCase().contentEquals("help") || line.toLowerCase().contentEquals("6") ){
                ps("\nInserisci nome comando o numero.\n\n1. [GIOCA]: Inizia un nuovo round di wordle;\n2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;\n3. [CONDIVIDI]: Condividi i risultati del gioco sul social;\n4. [NOTIFICHE]: Mostra notifiche di gioco inviate dai giocatori;\n5. [ESCI]: Termina la sessione di gioco\n6. [HELP PER LEGENDA].");
            }else if(line.toLowerCase().contentEquals("gioca") || line.toLowerCase().contentEquals("1")){ //play wordle
                this.playWORDLE();
            }else if(line.toLowerCase().contentEquals("statistiche") || line.toLowerCase().contentEquals("2")){ //send me statistic 
                this.sendMeStatistics();
            }else if(line.toLowerCase().contentEquals("condividi") || line.toLowerCase().contentEquals("3")){ //share
                this.share();
            }else if(line.toLowerCase().contentEquals("notifiche") || line.toLowerCase().contentEquals("4")){ //show me sharing
                this.showMeSharing();
            }else if(line.toLowerCase().contentEquals("esci") || line.toLowerCase().contentEquals("5")){ //exit
                this.closeMultiCast();
                this.logout();
                return 1;
            }else{
                ps("Hai inserito un comando non valido!");
            }
        }
        return 1;
    }

    // metodo per richiede di registrarsi nel sistema
    public int register(){
        int n = 0;
        while (true){
            ps("Inserisci il nome utente");
            USERNAME = ReceiveInput();   
            if (USERNAME.contains(" ")){
                ps("Hai inserito un carattere non valido, riprova!");
            }else{break;}
        }
        ps("Inserisci la password");
        PASSWORD = ReceiveInput();      
        this.Send("Registra");
        this.Send(USERNAME);
        this.Send(PASSWORD);
        try{
            n = Integer.parseInt(this.Receive());
            if(n == 1){
                ps("[SUCCESS]\nREGISTRAZIONE AVVENUTA CON SUCCESSO!\n");
            }else if(n == -1){
                ps("[ERROR1]\nUSERNAME GIA' ESISTENTE!\nACCEDI COL TUO USERNAME O SCEGLI UN ALTRO.");
            }else if(n == -2){
                ps("[ERROR2]\nPASSWORD NON EFFICACE!");
            }
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
        }
        return n;
    }

    // metodo per richiede di accedere al sistema
    public int login(){
        int n = 0;
        ps("Inserisci il nome utente");
        USERNAME = ReceiveInput();
        ps("Inserisci la password");
        PASSWORD = ReceiveInput();  
        this.Send("Accedi");
        this.Send(USERNAME);
        this.Send(PASSWORD);
        try {
            n = Integer.parseInt(this.Receive());
            if(n == 1){
                ps("[SUCCESS]\nACCESSO AVVENUTO CON SUCCESSO!");
            }else if(n == -1){
                ps("[ERROR1]\nUSERNAME NON ESISTENTE, REGISTRATI!");
            }else if(n == -2){
                ps("[ERROR2]\nPASSWORD SBAGLIATA, RIPROVA!");
            }else if(n == -3){
                ps("[ERROR3]\nUTENTE GIA' PRESENTE IN UN ALTRA SESSIONE!");
            }
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
        }
        return n;
    }

    //metodo per richiede di uscire dal sistema
    public void logout(){
        //disconnessione utente dalla sessione di gioco
        ps("\nRICHIESTA DISCONNESSIONE SESSIONE UTENTE: "+USERNAME+" ...");
        this.Send("esci");
    }

    //1. metodo per richiede di iniziare a giocare a WORDLE 
    public int playWORDLE(){
        //richiesta di iniziare la sessione di gioco 
        int n = 1;
        int round = 0;
        ps("\nRICHIESTA DI INIZARE A GIOCARE...");
        this.Send("gioca");
        try{
            int i = Integer.parseInt(this.Receive());
            if(i == 1){
                ps("\nRICHIESTA ANDATA A BUON FINE, INIZIO GIOCO!");
                round = Integer.parseInt(this.Receive());
                suggerimenti = "";
                shareInfo = "Nome utente: "+USERNAME+" - Round Wordle " + round + ": ";
                TENTATIVO = 0;
                this.startGuessing();
            }else{
                ps("\nGIOCATORE HA GIA' PARTECIPATO A QUESTA SESSIONE, ASPETTA LA PROSSIMA!");
            }
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
        }
        return n;
    }


    //inizia a mandare le parole da indovinare
    public void startGuessing(){

        String user_input;
        int n = 0;
        try{
            for(TENTATIVO=0; TENTATIVO<12; ){
                ps("\nTENTATIVO NUMERO: "+ (TENTATIVO + 1) +"/12\n\nInserisci nome comando o numero.\n1. [GUESSED WORD]: Inserisci la tua guessed word;\n2. [ESCI]: Termina la sessione di gioco.\n");
                user_input = ReceiveInput();
                if(user_input.toLowerCase().contentEquals("esci") || user_input.toLowerCase().contentEquals("2")){
                    //richiesta di abbandono della sessione di gioco iniziare -> perdo tentativi e aggiorno stat
                    ps("\nHai abbandonato la sessione di gioco. Statistiche aggiornate.");
                    this.logout();
                    break;
                }else if(user_input.toLowerCase().contentEquals("guessed word") || user_input.toLowerCase().contentEquals("1")){
                    //inizia il tentativo di indovinare la parola
                    n = this.sendWord();
                }else{
                    ps("Inserito comando sbagliato, riprova!");
                    continue;
                }
                if(n == 10 || n == -2){
                    //se ho indovinato o è estratta nuova parola finisce la sessione
                    break;
                }
            }
            ps("\nRound di Wordle Terminato!");
            shareInfo = shareInfo + TENTATIVO+"/12\n" + suggerimenti;
        }catch (Exception e){
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
            return;
        }
    }

    public int sendWord() throws IOException{
        String esito;
        String UserSuggest;
        String UDPSuggest;
        ps("\nInserisci la parola [GUESSED WORD]:\n");
        String guessed_word;
        while(true){
            guessed_word = ReceiveInput();
            if(guessed_word.length()<10){
                ps("\nParola immessa troppo corta. Deve essere di 10 caratteri\n");
                continue;
            }else if(guessed_word.length()>10){
                ps("\nParola immessa troppo lunga. Deve essere di 10 caratteri\n");
                continue;
            }else{
                break;
            }
        }
        ps("\nParola inserita: "+guessed_word);
        //mando la parola scelta al server che elabora i suggerimenti
        this.Send(guessed_word);
        //ricevo la risposta dal server riguardo la parola mandata
        esito = this.Receive();
        if(esito.contentEquals("WORD_NOT_IN_ERROR_CODE")){ //errore parola non presente
            ps("\nParola non presente nel vocabolario, tentativo non contato. Riprova.");
            return -1;
        }else if(esito.contentEquals("NEW_WORD_ERROR_CODE")){ // errore estratta nuova parola
            ps("\nE' stata estratta una nuova parola! Le statistiche verranno aggiornate.");
            return -2;
        }else{
            //tentativo ok e aggiorno i dati del round
            TENTATIVO++;
            UserSuggest = this.Receive();
            UDPSuggest = this.Receive();
            suggerimenti = suggerimenti +"\n" + UDPSuggest;
            ps("\nLettere indovinate: "+esito+"\n\nSuggerimento:\n"+UserSuggest+"\n"+UDPSuggest);
            if (esito.contentEquals("10")){
                ps("\nHai indovinato la Secret Word di questo round!\n\nAspetta la prossima parola estratta.");
                return 10;
            }
        }
        return 0;
    }
    
    //2. richiesta di statistiche aggiornate all ultimo gioco  
    public void sendMeStatistics(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DELL'UTENTE: "+USERNAME+" ...\n");
        this.Send("showStats");
        String res = "";
        try{
            while(!res.contentEquals("EOF")){
                res = this.Receive();
                if(res.contentEquals("EOF")){break;}
                ps(res);
            }
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
        }
    }

    //3. richiesta di condividere le statistiche in un social (multicast);
    public void share(){
        ps("\nRICHIESTA DI CONDIVIDERE RISULTATI SUL SOCIAL ...\n");
        if (shareInfo.contentEquals("")){
            ps("\nNon c'è nessun dato da condividere! Gioca una partita!\n");
            return;
        }
        this.Send("shareStats");
        //ps("messaggio da mandare:\n"+shareInfo);
        this.Send(shareInfo);
        this.Send("EOF");
        try {
            if(this.Receive().contentEquals("OK_SHARE")){
                ps("...\n\nCONDIVISIONE ANDATA A BUON FINE");
            } 
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
            running = false;
        }
      
    }

    //4. mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti.
    public void showMeSharing(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DEGLI ALTRI UTENTI ...\n");
        int i = 1;
        if(LIST_MESSAGE.size() == 0){
            ps("Nessun giocatore ha condiviso i suggerimenti! Aspetta che qualcuno lo faccia");
            return;
        }
        ps("\nRicevuto pacchetti:\n");
        for (String message: LIST_MESSAGE){
            ps("Messaggio " + i + ":\n" + message);
            i++;
        }
    }

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    public void Send(String str) {
        //client invia la parola al server
        dataOut.println(str);
    }

    public String Receive() throws IOException{
        //client riceve parola dal server
        String str = "";
        str = dataIn.readLine();
        return str;
    }

    public String ReceiveInput(){
        //client riceve parola dall'utente 
        String str = "";
        str = stdin.nextLine();
        return str;
    }

    //metodo per leggere i parametri dei metodi
	public static void readConfig() throws FileNotFoundException, IOException {
        ps("------- PARAMETERS SETTINGS  ---------");
        //configuration file per ottenere tutte le informazioni utili come parametri o valori defualt 
		InputStream in = new FileInputStream("Config/Client.properties");
        Properties props = new Properties();
        props.load(in);
        SOCKET_PORT = Integer.parseInt(props.getProperty("socket_port"));
        SOCKET_ADDRESS = props.getProperty("socket_address");
        MULTICAST_ADDRESS = props.getProperty("multicast_socket_address");
        MULTICAST_PORT = Integer.parseInt(props.getProperty("multicast_socket_port"));
        BUFF_LENGTH = Integer.parseInt(props.getProperty("buff_length"));
        in.close();
	}


    public static void main(String[] args) throws IOException{
        ps("------- CLIENT SIDE -------");
        //System.out.println("\u001B[33mYellow text");
        //System.out.println("\u001B[34mBlue text");
        //System.out.println("\u001B[35mPurple text");
        //System.out.println("\u001B[36mCyan text");
        //System.out.println("\u001B[37mWhite text");        
        try {
            readConfig();
            WordleClientMain CLIENT = new WordleClientMain();
            CLIENT.startClient();
            //ps("FINE CLIENT\n");
            //TERMINAZIONE PROGRAMMA
        } catch (Exception e) {
            System.err.printf("ERROR: SERVER CRASHATO - RIPROVA PIU TARDI\n");
        }
    }
}
