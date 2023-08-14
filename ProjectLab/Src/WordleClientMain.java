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
    private Scanner dataIn;
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

    // costruttore wordleclientmain inizializzo canale socket 
    public WordleClientMain(){
        stdin = new Scanner(System.in);
        try {        
            SOCKET = new Socket(SOCKET_ADDRESS, SOCKET_PORT);
            ps("CONNESSIONE AVVENUTA CON SUCCESSO"); 
            this.createCommunication();
        } catch (IOException e) {
            System.err.printf("ERROR client main : "+e.getMessage());
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
            dataIn = new Scanner(SOCKET.getInputStream());
            dataOut = new PrintWriter(SOCKET.getOutputStream(),true);
            PLAYER_ID = this.Receive();
        } catch (IOException e) {
            System.err.printf("ERROR communication: "+e.getMessage());
            System.exit(1);        
        }
    }

    // utilizzato per chiudere i canali di comunicazione
    public void closeCommunication(){
        try {
            dataIn.close();
            dataOut.close();
        } catch (Exception e) {
            System.err.printf("ERROR data stream: "+e.getMessage());
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
            Thread multicast_thread = new Thread(new ThreadMultiCast("Client"+PLAYER_ID, MULTICAST_SOCKET, LIST_MESSAGE));
            multicast_thread.start();
        } catch (SocketException e) {
            System.err.printf("ERROR setupmulticast: "+e.getMessage());
            System.exit(1);        
        }
    }

    // metodo per lasciare il gruppo multicast e chiuderlo
    public void closeMultiCast(){
        try {
            MULTICAST_SOCKET.leaveGroup(MULTICAST_GROUP);
            MULTICAST_SOCKET.close();
        } catch (Exception e) {
            System.err.printf("ERROR closemulticast: "+e.getMessage());
            System.exit(1);         
        }
    }

    //metodo per iniziare ad inserire stringhe ma il gioco non è ancora iniziato
    public void startClient() throws IOException{
        String mode;
        int n;
        try{
            while(true){
                ps("\nBenvenuto in Wordle!\nSeleziona una opzione:\n\n1. [REGISTRAZIONE]\n2. [ACCESSO]");
                mode = ReceiveInput();
                if(mode.toLowerCase().contentEquals("registrazione") || mode.toLowerCase().contentEquals("1")){
                    ps("Inserisci il nome utente");
                    USERNAME = ReceiveInput();
                    ps("Inserisci la password");
                    PASSWORD = ReceiveInput();             
                    this.register(USERNAME, PASSWORD);
                }else if(mode.toLowerCase().contentEquals("accesso") || mode.toLowerCase().contentEquals("2")){
                    ps("Inserisci il nome utente");
                    USERNAME = ReceiveInput();
                    ps("Inserisci la password");
                    PASSWORD = ReceiveInput();
                    if(this.login(USERNAME,PASSWORD) == 1){
                        //se login effettuato con successo mi unisco al gruppo multicast
                        this.setupMultiCast();
                        n = this.startSession();
                        if (n == -1){
                            return;
                        }
                    }
                }else{
                    ps("\nOPZIONE ERRATA, RIPROVA.");
                }
            }
        }catch (Exception e){
            System.err.printf("ERROR startclient: "+e.getMessage()+"\nServer crashato, riprova piu tardi");
            System.exit(1); 
        }
    }

    //inizia la sessione dell'utente il quale può effettuare diverse operazioni
    public int startSession()  throws IOException{  
        String line;
        int n = 1;
        try {
            while(true){
                ps("\nInserisci nome comando o numero.\n\n1. [GIOCA]: Manda richiesta per iniziare un nuovo round di wordle;\n2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;\n3. [CONDIVIDI]: Richiesta di condividere i risultati del gioco sul social;\n4. [MSG]: Mostra notifiche di gioco inviate dai giocatori;\n5. [ESCI]: Termina la sessione di gioco\n6. [HELP PER LEGENDA].");
                line = ReceiveInput();
                if(line.toLowerCase().contentEquals("help") || line.toLowerCase().contentEquals("6") ){
                    ps("\nInserisci nome comando o numero.\n1. [GIOCA]: Manda richiesta per iniziare un nuovo round di wordle;\n2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;\n3. [CONDIVIDI]: Richiesta di condividere i risultati del gioco sul social;\n4. [MSG]: Mostra notifiche di gioco inviate dai giocatori;\n5. [ESCI]: Termina la sessione di gioco\n6. [HELP PER LEGENDA].");
                }else if(line.toLowerCase().contentEquals("gioca") || line.toLowerCase().contentEquals("1")){
                    n = this.playWORDLE();
                }else if(line.toLowerCase().contentEquals("statistiche") || line.toLowerCase().contentEquals("2")){
                    n = this.sendMeStatistics();
                    if (n == -1) return -1;
                }else if(line.toLowerCase().contentEquals("condividi") || line.toLowerCase().contentEquals("3")){
                    this.share();
                }else if(line.toLowerCase().contentEquals("msg") || line.toLowerCase().contentEquals("4")){
                    n = this.showMeSharing();
                    if (n == -1) return -1;
                }else if(line.toLowerCase().contentEquals("giocatori")){
                    this.showPlayersLogged();
                }else if(line.toLowerCase().contentEquals("esci") || line.toLowerCase().contentEquals("5")){
                    this.logout();
                    this.closeMultiCast();
                    return 1;
                }else if(line.toLowerCase().contentEquals("closeserver")){
                    this.closeServer();
                    return 1;
                }else if(line.contentEquals("UTENTECRASHATOERRORCODE")){
                    ps("Crash del sistema, esco....");
                    return -1;
                }else{
                    ps("Hai inserito un comando non valido!");
                }
            }
        }catch (Exception e){
            System.err.printf("ERROR startsession: "+e.getMessage()+"\nServer crashato, riprova piu tardi");
            System.exit(1); 
        }
        return n;
    }

    // metodo per richiede di registrarsi nel sistema
    public int register(String username, String psw){
        int n = 0;
        this.Send("Registra");
        this.Send(username);
        this.Send(psw);
        n = Integer.parseInt(this.Receive());
        if(n == 1){
            ps("[SUCCESS]\nREGISTRAZIONE AVVENUTA CON SUCCESSO!\n");
        }else if(n == -1){
            ps("[ERROR1]\nUSERNAME GIA' ESISTENTE!\nACCEDI COL TUO USERNAME O SCEGLI UN ALTRO.");
        }else if(n == -2){
            ps("[ERROR2]\nPASSWORD NON EFFICACE!");
        }
        return n;
    }

    // metodo per richiede di accedere al sistema
    public int login(String username, String psw){
        int n = 0;
        this.Send("Accedi");
        this.Send(username);
        this.Send(psw);
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
        return n;
    }


    //1. metodo per richiede di iniziare a giocare a WORDLE 
    public int playWORDLE() throws IOException{
        //richiesta di iniziare la sessione di gioco 
        int n = 1;
        int round;
        ps("\nRICHIESTA DI INIZARE A GIOCARE...");
        this.Send("gioca");
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
        return n;
    }


    //inizia a mandare le parole da indovinare
    public void startGuessing(){

        String user_input;
        int n = 0;
        try{
            ps("\nInserisci la parola [GUESSED WORD].");
            while(TENTATIVO < 12){
                ps("\nTENTATIVO NUMERO: "+ (TENTATIVO + 1) +" - Inserisci la tua guessed word:\n");
                user_input = ReceiveInput();
                if(user_input.toLowerCase().contentEquals("esci")){
                    ps("\nHai abbandonato la sessione di gioco. Statistiche aggiornate.");
                    this.logout();
                    break;
                }else if(user_input.length()<10){
                    ps("\nParola immessa troppo corta. Deve essere di 10 caratteri");
                    continue;
                }else if(user_input.length()>10){
                    ps("\nParola immessa troppo lunga. Deve essere di 10 caratteri");
                    continue;
                }
                n = this.sendWord(user_input);
                if(n == 10 || n == -2){
                    break;
                }
            }
            ps("\nRound di Wordle Terminato!");
            shareInfo = shareInfo + TENTATIVO+"/12\n" + suggerimenti;
            TENTATIVO = 1;
            //this.logout();
            //this.sendMeStatistics();
        }catch (Exception e){
            System.err.printf("ERROR startguessing: "+e.getMessage()+"\nServer crashato, riprova piu tardi");
            System.exit(1);   
        }
    }

    public int sendWord(String user_input){
        String esito;
        String suggerimento;
        ps("\nParola inserita: "+user_input);
        this.Send(user_input);
        //ricevo la risposta dal server riguardo la parola mandata
        esito = this.Receive();
        if(esito.contentEquals("PAROLANONPRESENTEERRORCODE")){ //errore non presente
            ps("\nParola non presente nel vocabolario, tentativo non contato. Riprova.");
            return -1;
        }else if(esito.contentEquals("NEWWORDERRORCODE")){ // errore estratta nuova parola
            ps("\nE' stata estratta una nuova parola! Le statistiche verranno aggiornate.");
            return -2;
        }else{
            TENTATIVO++;
            suggerimento=this.Receive();
            suggerimenti = suggerimenti +"\n" + suggerimento;
            ps("\nLettere indovinate: "+esito+"\n\nSuggerimento: "+suggerimento);
            if (esito.contentEquals("10")){
                ps("\nHai indovinato la Secret Word di questo round!\n\nAspetta la prossima parola estratta.");
                return 10;
            }
        }
        return -1;
    }
    
    //2. richiesta di statistiche aggiornate all ultimo gioco  
    public int sendMeStatistics(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DELL'UTENTE: "+USERNAME+"\n\n...\n");
        this.Send("showStats");
        String res = "";
        while(!res.contentEquals("EOF")){
            res = this.Receive();
            if(res.contentEquals("EOL")){break;}
            else if(res.contentEquals("UTENTECRASHATOERRORCODE")){
                ps("Crash del sistema, esco....");
                return -1;
            }
            ps(res);
        }
        return 1;
    }

    //3. richiesta di condividere le statistiche in un social (multicast);
    public void share(){
        ps("\nRICHIESTA DI CONDIVIDERE RISULTATI SUL SOCIAL...\n");
        this.Send("shareStats");
        this.Send(shareInfo);
        this.Send("EOF");
    }

    //4. mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti.
    public int showMeSharing(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DEGLI ALTRI UTENTI...\n");
        int i = 1;
        ps("\nRicevuto pacchetti:\n\n");
        for (String message: LIST_MESSAGE){
            ps("Messaggio " + i + " : " + message);
            i++;
        }
        return 1;
    }

    //5. metodo per richiede di uscire dal sistema
    public void logout(){
        //disconnessione utente dalla sessione di gioco
        ps("\nRICHIESTA DISCONNESSIONE SESSIONE UTENTE: "+USERNAME+"\n\n...");
        this.Send("esci");
    }

    //metodo per visualizzare i giocatori loggati nel server
    public void showPlayersLogged(){
        ps("\nRICHIESTA VISUALIZZAZIONE GIOCATORI CONNESSI\n");
        Send("showConnectedPlayer");
    }
    
    //metodo per chiudere il server in maniera più carina e appropriata
    public void closeServer(){
        ps("\nRICHIESTA DISCONNESSIONE UTENTE E CHIUSURA SERVER\n");
        Send("closeserver");
    }

    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    public void Send(String str){
        //client invia la parola al server
        try {
            dataOut.println(str);
        } catch (Exception e) {
            System.err.printf("ERROR send stream data: "+e.getMessage());
            System.exit(1);   
        }
    }

    public String Receive(){
        //client riceve parola dal server
        String str = "";
        try {
            str = dataIn.nextLine();
        } catch (Exception e) {
            System.err.printf("ERROR receive stream data: "+e.getMessage());
            System.exit(1);   
        }  
        return str;
    }

    public String ReceiveInput(){
        //client riceve parola dal server
        String str = "";
        try {
            str = stdin.nextLine();
        } catch (Exception e) {
            System.err.printf("ERROR receive user input: "+e.getMessage());
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
        ps("------ CLIENT SIDE ------");
        try {
            readConfig();
            WordleClientMain CLIENT = new WordleClientMain();
            CLIENT.startClient();
        } catch (Exception e) {
            System.err.printf("ERROR clientmain: "+e.getMessage());
            System.exit(1);   
        }
    }
}
