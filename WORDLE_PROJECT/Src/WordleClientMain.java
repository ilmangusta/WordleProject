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
    
    private Socket SOCKET;
    private BufferedReader dataIn;
    private PrintWriter dataOut;
    public String PLAYER_ID;
    private int TENTATIVO;
    private static Scanner stdin;
    private static String USERNAME;
    private static String PASSWORD;
    private static int SOCKET_PORT;
    private static int MULTICAST_PORT; //porta per connessione socket 
    private static String MULTICAST_ADDRESS; //indirizzo per connessione socket multicast per condivisione social 
    private InetAddress MULTICAST_GROUP;
    private static String SOCKET_ADDRESS; //indirizzo per connessione con socket TCP
    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private String shareInfo = "";
    private String suggerimenti = "";
    private static List<String> LIST_MESSAGE; // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private ThreadMultiCast thread_multicast;
    private Thread thread_mc;

    // costruttore wordleclientmain inizializzo canale socket 
    public WordleClientMain(){
        stdin = new Scanner(System.in);
        try {        
            SOCKET = new Socket(SOCKET_ADDRESS, SOCKET_PORT);
            ps("CONNESSIONE AVVENUTA CON SUCCESSO"); 
            this.createCommunication();
        } catch (IOException e) {
            System.err.printf("\nERROR client main\n");
            System.exit(1);        
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
            if(!SOCKET.isClosed()){
                dataIn.close();
                dataOut.close(); 
                SOCKET.close();
            }
        } catch (Exception e) {
            System.err.printf("\nERROR close connection\n");
            System.exit(1);            
        }
    }

    
    //metodo per instaurare il gruppo multicast utilizzato per la condivisione sociale
    public void setupMultiCast() throws IOException{
        try{
            LIST_MESSAGE = Collections.synchronizedList(new ArrayList<String>());
            MULTICAST_SOCKET = new MulticastSocket(MULTICAST_PORT);
            MULTICAST_GROUP = InetAddress.getByName(MULTICAST_ADDRESS);
            MULTICAST_SOCKET.joinGroup(MULTICAST_GROUP); //deprecato
            //faccio partire il thread multicast che si occupa di ricevere in attesa i messaggi
            thread_multicast = new ThreadMultiCast(MULTICAST_SOCKET, LIST_MESSAGE);
            thread_mc = new Thread(thread_multicast);
            thread_mc.start();
        } catch (SocketException e) {
            System.err.printf("\nERROR setupmulticast\n");
            System.exit(1);        
        }
    }

    // metodo per lasciare il gruppo multicast e chiuderlo
    public void closeMultiCast(){
        try {
            if (!MULTICAST_SOCKET.isClosed()){
                MULTICAST_SOCKET.leaveGroup(MULTICAST_GROUP);
                MULTICAST_SOCKET.close();
                ps("\nABBANDONO LA PIATTAFORMA SOCIALE PER LA CONDIVISIONE ...");
            }
        } catch (Exception e) {
            System.err.printf("\nERROR closemulticast\n");
            System.exit(1);         
        }
    }

    //metodo per iniziare ad inserire stringhe ma il gioco non è ancora iniziato
    public void startClient() throws IOException{
        String mode;
        int n;
        boolean running = true;
        try{
            while(running){
                ps("\nBenvenuto in Wordle!\nSeleziona una opzione:\n\n1. [REGISTRAZIONE]\n2. [ACCESSO]\n3. [CHIUDI TERMINALE]\n");
                mode = ReceiveInput();
                if(mode.toLowerCase().contentEquals("registrazione") || mode.toLowerCase().contentEquals("1")){       
                    this.register();
                }else if(mode.toLowerCase().contentEquals("accesso") || mode.toLowerCase().contentEquals("2")){
                    if(this.login() == 1){
                        //se login effettuato con successo mi unisco al gruppo multicast
                        this.setupMultiCast();
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
            this.closeConnection();
            ps("\nCONNESSIONE CLIENT TERMINATA ... CHIUSURA ...");
        }catch (Exception e){
            System.err.printf("\nERROR startclient\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1); 
        }
    }

    //inizia la sessione dell'utente il quale può effettuare diverse operazioni
    public int startSession()  throws IOException{  
        String line;
        int n = 1;
        try {
            while(true){
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
                    this.logout();
                    this.closeMultiCast();
                    return 1;
                }else if(line.contentEquals("UTENTECRASHATOERRORCODE")){
                    ps("Crash del sistema, esco....");
                    return -1;
                }else{
                    ps("Hai inserito un comando non valido!");
                }
            }
        }catch (Exception e){
            System.err.printf("\nERROR startsession\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1); 
        }
        return n;
    }

    // metodo per richiede di registrarsi nel sistema
    public int register(){
        int n = 0;
        ps("Inserisci il nome utente");
        USERNAME = ReceiveInput();
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
            System.err.printf("\nERROR registrazione\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);         
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
            System.err.printf("\nERROR login\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);         
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
                shareInfo = "Round Wordle " + round + ": ";
                TENTATIVO = 0;
                this.startGuessing();
            }else{
                ps("\nGIOCATORE HA GIA' PARTECIPATO A QUESTA SESSIONE, ASPETTA LA PROSSIMA!");
            }
        } catch (Exception e) {
            System.err.printf("\nERROR playwordle\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);         
        }
        return n;
    }


    //inizia a mandare le parole da indovinare
    public void startGuessing(){

        String user_input;
        int n = 0;
        try{
            while(TENTATIVO < 12){
                ps("\nTENTATIVO NUMERO: "+ (TENTATIVO + 1) +"\n\n1. [GUESSED WORD]: Inserisci la tua guessed word\n2. [ESCI]: Termina la sessione di gioco:\n");
                user_input = ReceiveInput();
                if(user_input.toLowerCase().contentEquals("esci") || user_input.toLowerCase().contentEquals("2")){
                    //richiesta di abbandono della sessione di gioco iniziare -> perdo tentativi e aggiorno stat
                    ps("\nHai abbandonato la sessione di gioco. Statistiche aggiornate.");
                    this.logout();
                    break;
                }else{
                    //inizia il tentativo di indovinare la parola
                    n = this.sendWord();
                }
                if(n == 10 || n == -2){
                    //se ho indovinato o è estratta nuova parola finisce la sessione
                    break;
                }
            }
            ps("\nRound di Wordle Terminato!");
            shareInfo = shareInfo + TENTATIVO+"/12\n" + suggerimenti;
            TENTATIVO = 1;
        }catch (Exception e){
            System.err.printf("ERROR startguessing\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);   
        }
    }

    public int sendWord(){
        String esito;
        String suggerimento;
        ps("\nInserisci la parola [GUESSED WORD].");
        String guessed_word = ReceiveInput();

        if(guessed_word.length()<10){
            ps("\nParola immessa troppo corta. Deve essere di 10 caratteri");
            return 0;
        }else if(guessed_word.length()>10){
            ps("\nParola immessa troppo lunga. Deve essere di 10 caratteri");
            return 0;
        }
        ps("\nParola inserita: "+guessed_word);
        //mando la parola scelta al server che elabora i suggerimenti
        this.Send(guessed_word);
        //ricevo la risposta dal server riguardo la parola mandata
        try{
            esito = this.Receive();
            if(esito.contentEquals("PAROLANONPRESENTEERRORCODE")){ //errore non presente
                ps("\nParola non presente nel vocabolario, tentativo non contato. Riprova.");
                return -1;
            }else if(esito.contentEquals("NEWWORDERRORCODE")){ // errore estratta nuova parola
                ps("\nE' stata estratta una nuova parola! Le statistiche verranno aggiornate.");
                return -2;
            }else{
                //tentativo ok e aggiorno i dati del round
                TENTATIVO++;
                suggerimento=this.Receive();
                suggerimenti = suggerimenti +"\n" + suggerimento;
                ps("\nLettere indovinate: "+esito+"\n\nSuggerimento: "+suggerimento);
                if (esito.contentEquals("10")){
                    ps("\nHai indovinato la Secret Word di questo round!\n\nAspetta la prossima parola estratta.");
                    return 10;
                }
            }
        } catch (Exception e) {
            System.err.printf("\nERROR send word\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);         
        }
        return -1;
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
            System.err.printf("\nERROR send me statistics\nSERVER CRASHATO - RIPROVA PIU TARDI\n");
            System.exit(1);         
        }
    }

    //3. richiesta di condividere le statistiche in un social (multicast);
    public void share(){
        ps("\nRICHIESTA DI CONDIVIDERE RISULTATI SUL SOCIAL ...\n");
        if (shareInfo.contentEquals("")){
            ps("\nNon c'è nessun dato da condividere! Gioca una partita!");
            return;
        }
        this.Send("shareStats");
        this.Send(shareInfo);
        this.Send("EOF");
    }

    //4. mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti.
    public void showMeSharing(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DEGLI ALTRI UTENTI ...\n");
        int i = 1;
        if(LIST_MESSAGE.size() == 0){
            ps("Nessun giocatore ha condiviso i suggerimenti! Aspetta che qualcuno lo faccia");
            return;
        }
        ps("\nRicevuto pacchetti:\n\n");
        for (String message: LIST_MESSAGE){
            ps("Messaggio " + i + " : " + message);
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
        //client riceve parola dal server
        String str = "";
        try {
            str = stdin.nextLine();
        } catch (Exception e) {
            System.err.printf("ERROR receive user input\n");
            System.exit(1);   
        }  
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
        in.close();
	}


    public static void main(String[] args) throws IOException{
        ps("------- CLIENT SIDE -------");
        try {
            readConfig();
            WordleClientMain CLIENT = new WordleClientMain();
            CLIENT.startClient();
        } catch (Exception e) {
            System.err.printf("ERROR clientmain\n");
            System.exit(1);   
        }
    }
}
