package Src;

//package Src;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


// da gestiree: se un utente crasha con ctrl c e prova ad effettuare nuovamente accesso risulta gia loggato
// ricevere segnale sig ctrl c e rimuovere utente dalla sessione



public class ThreadServer implements Runnable {

    //private boolean aggiornaparola = false;
    private Scanner dataIn; //scanner per input
    private PrintWriter dataOut; //canale per output
    private String Line = ""; //"buffer" per comunicazione
    private String USERNAME; 
    private String PASSWORD;
    private String SECRET_WORD; // secret words estratta da indovinare
    private AtomicInteger GAME_ID; // identificativo della sessione di gioco di wordle (numero intero)
    private int TEMP_GAME_ID; // id temporaneo per ricordarsi l id del game a cui il giocatore sta partecipando
    private int TEMP_WORD_ID; // id temporaneo per ricordarsi la parola che il giocatore sta cercando di giocare
    private int PLAYER_ID; //id per ricordare il giocatore
    private int WORDS_LENGTH; // lunghezza della lista di secret words estratte
    private String FILE_NAME_WORDS; 
    private String FILE_NAME_DB;
    private Boolean GAME_STARTED = false;
    private List<String> SECRET_WORDS = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private List<String> DB_UTENTI_CONNESSI;  // una lista sincronizzata per ricordare chi è connesso
    private ArrayList<Giocatore> DB_STATS_GIOCATORI; //una lista di giocatori, utilizzata per l'aggiornamento delle statistiche dei giocatori
    private ConcurrentHashMap<String,String> DB_UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema coppia valori username - password
    private ConcurrentHashMap<String,ArrayList<String>> DB_PAROLE_GIOCATE;  // una hashmap con coppia Giocatore - Tutte le parole giocate
    private Socket SOCKET; //socket che sarà utilizzato per la conunicazione
    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private int MULTICAST_PORT; // porta per la comunicazione del gruppo multicast
    private InetAddress MULTICAST_GROUP; // indirizzo per la comunicazione

    public ThreadServer(Socket s, MulticastSocket mc_socket, int mc_port, InetAddress mc_group, int player_id, AtomicInteger game_id, ArrayList<Giocatore> dbGiocatori,ConcurrentHashMap<String,ArrayList<String>> dbParole, ConcurrentHashMap<String,String> dbUtentiRegistrati, List<String> DBConnessi, String secret_word, List<String> secret_words, String file_name_words, String file_name_db){
        SOCKET = s;
        MULTICAST_SOCKET = mc_socket;
        MULTICAST_PORT = mc_port;
        MULTICAST_GROUP = mc_group;
        PLAYER_ID = player_id;
        GAME_ID = game_id;
        DB_STATS_GIOCATORI = dbGiocatori;
        DB_PAROLE_GIOCATE = dbParole;
        DB_UTENTI_REGISTRATI = dbUtentiRegistrati;
        DB_UTENTI_CONNESSI = DBConnessi;
        WORDS_LENGTH = secret_words.size() - 1;
        SECRET_WORDS = secret_words;
        SECRET_WORD = secret_words.get(WORDS_LENGTH);
        FILE_NAME_WORDS = file_name_words;
        FILE_NAME_DB = file_name_db;
        this.createCommunication();
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
        } catch (IOException e) {
            System.err.printf("ERROR createcommunication: "+e.getMessage());
            System.exit(1);
        }
    }

    // utilizzato per chiudere i canali di comunicazione
    public void closeCommunication(){
        try {
            dataIn.close();
            dataOut.close();            
        } catch (Exception e) {
            System.err.printf("ERROR closecommunication: "+e.getMessage());
            System.exit(1);            
        }

    }

    //metodo per evitare di fare ctrl c sul server per spegnerlo
    public void closeServer(){
        try {
            SOCKET.close();
        }catch (Exception e){
            System.err.printf("ERROR closing server: "+e.getMessage());
            System.exit(1);  
        }
        ps("Closed server...");
    }

    //metodo che fa partire l'esecuzione del thread con le due principali attivita registrazione o login
    public void run(){ 
        //thread run partono con id diversi ognuno indipendente connesso col proprio id client (Giocatore)
        int n = 0;
        try {
            dataOut.println(PLAYER_ID);
            while(dataIn.hasNextLine()){ 
                //controllo se è stata estratta una nuova parola
                checkSecretWord();
                //ciclo infinito per mandare messaggi al client e ricevere messaggi
                Line =this.Receive();
                if(Line.contentEquals("Registra") || Line.contentEquals("Accedi")){
                    USERNAME = this.Receive();
                    PASSWORD = this.Receive();
                    if(Line.contentEquals("Registra")){
                        n = this.register(USERNAME,PASSWORD);
                        this.Send(Integer.toString(n));
                    }else if(Line.contentEquals("Accedi")){
                        n = this.login(USERNAME,PASSWORD);
                        this.Send(Integer.toString(n));
                        if (n == 1){
                            this.startSession();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("ERROR thread client: "+e.getMessage());
            System.exit(1);  
        }
    }

    //metodo che descrive le principali operazioni effettuate dopo che l'utente effettua il login
    public void startSession() throws IOException{

        try {
            while(dataIn.hasNextLine()){ 
                ps("\nnext line ");
                //ciclo infinito per mandare messaggi al client e ricevere messaggi
                checkSecretWord();
                Line = this.Receive();
                if(Line.contentEquals("gioca")){ //1.
                    int n = this.checkPlayer(USERNAME);
                    if (n == 1){
                        this.startGame();
                    }
                }else if(Line.contentEquals("showConnectedPlayer")){
                    this.showConnectedPlayer();
                }else if(Line.contentEquals("showStats")){ //2.
                    this.showStats(USERNAME);
                }else if(Line.contentEquals("shareStats")){ //3.
                    this.shareStats(USERNAME);
                }else if(Line.contentEquals("statsPlayers")){ //4. 
                    this.showStats("Giocatori");
                }else if(Line.contentEquals("esci")){ //5.
                    this.logout(USERNAME, false);
                    return;
                }else if(Line.contentEquals("closeserver")){
                    this.logout(USERNAME, false);
                    this.closeServer();
                    return;
                }
            }
        } catch (Exception e) {
            this.logout(USERNAME, false);
            this.Send("UTENTECRASHATOERRORCODE");
            System.err.println("ERROR startsession: Termino sessione utente: "+e.getMessage());
            System.exit(1);  
        }
    }


    //metodo che descrive le 2 principali operazioni effettuate durante la sessione di gioco
    public void startGame() throws IOException{
        int TENTATIVO = 0;
        try {
            while(dataIn.hasNextLine()){ 
                //ciclo infinito per mandare messaggi al client e ricevere messaggi
                //Line = dataIn.readLine(); DEPRECATO
                if (checkSecretWord() == 1){
                    ps("Estratta nuova parola: "+SECRET_WORD);
                    this.Send("NEWWORDERRORCODE");
                    this.logout(USERNAME, false);
                    return;
                }
                Line = this.Receive();
                if(Line.contentEquals("esci")){ 
                    ps("richiesta logout");
                    this.logout(USERNAME,false);
                    return;
                }else{
                    int n = this.checkWord(Line);
                    if (n == -1){
                        ps("Parola non presente nel db parole");
                        continue;
                    }
                    TENTATIVO++;
                    ps("Parola: "+Line);
                    if (n == 10){
                        this.logout(USERNAME, true);
                        return;
                    }
                    if (TENTATIVO == 12){
                        this.logout(USERNAME, false);
                        return;
                    }
                } 
            }
        } catch (Exception e) {
            Send("UTENTECRASHATOERRORCODE");
            this.logout(USERNAME,false);
            System.err.println("ERROR startgame: Termino sessione utente: "+e.getMessage());
            System.exit(1);  
        }
    }


    //servizio registrazione utente al gioco 
    public int register(String username, String password) throws IOException{
        if(DB_UTENTI_REGISTRATI.containsKey(username)){
            ps("GIOCATORE GIA' REGISTRATO!");
            return -1;
        }
        if(password.length()<2){
            return -2;
        }
        else{
            DB_STATS_GIOCATORI.add(new Giocatore(username,password,new ArrayList<String>(),0,0,0,0,0,0,0));
            DB_UTENTI_REGISTRATI.put(username,password);
            DB_PAROLE_GIOCATE.put(username,new ArrayList<String>());
            this.updateJson();
            ps("GIOCATORE REGISTRATO CON SUCCESSO! LISTA GIOCATORI REGISTRATI:\n"+DB_UTENTI_REGISTRATI);
            return 1;
        }
    }

    //servizio di login utente al gioco 
    public int login(String username, String password){
        if(!DB_UTENTI_REGISTRATI.containsKey(username)){
            ps("GIOCATORE NON REGISTRATO");
            return -1;
        }else if(!password.contentEquals(DB_UTENTI_REGISTRATI.get(username))){
            ps("PASSWORD NON CORRETTA");
            return -2;
        }else if(DB_UTENTI_CONNESSI.contains(username)){
            ps("UTENTE GIA' PRESENTE IN SESSIONE");
            return -3;
        }
        DB_UTENTI_CONNESSI.add(username);
        ps("GIOCATORE ESEGUITO ACCESSO CON SUCCESSO. LISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
        return 1;
    }

    //servizio di logout giocatore dal gioco
    public void logout(String username, boolean indovinata) throws IOException{
        //if(DBUtentiConnessi.containsKey(username)){
        if(DB_UTENTI_CONNESSI.contains(username)){
            if(GAME_STARTED){
                ps("AGGIORNO STATISTICHE GIOCATORE: "+username);
                this.updateStats(USERNAME, indovinata);
            }else{
                DB_UTENTI_CONNESSI.remove(username);
                ps("DISCONNESSIONE UTENTE: "+username+".\nLISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
            }

        }else{
            ps("GIOCATORE NON PRESENTE IN SESSIONE!");
        }
        GAME_STARTED = false;
    }

    //metodo di debug per controllare lato server i giocatori connessi in un preciso istante
    public void showConnectedPlayer(){
        ps("LISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
    }

    //2-4. metodo per mostrare le statistiche sia del giocatore che in alternativa di tutti i giocatori 
    public void showStats(String str) throws IOException{
        String res = "";
        try {
            if(str.contentEquals("Giocatori")){
                for (Giocatore g: DB_STATS_GIOCATORI) {
                    if(g.username.equals(USERNAME)){
                        continue;
                    }
                    res+= "STATISTICHE DEL GIOCATORE: "+g.username+"\nNumero partite giocate: "+g.partiteGiocate+"\nNumero partite vinte: "+g.partiteVinte+"\nPercentuale partite vinte: "+g.percentualeVinte+"\nStreak attuale: "+g.streakVincite+"\nStreak più lunga: "+g.maxStreakVincite+"\nGuess Distribution: "+g.guessDistribution+"\n\n";
                }
                res+= "EOL";
                this.Send(res);
            }else{
                res+= "STATISTICHE DEL GIOCATORE: ";
                for (Giocatore g: DB_STATS_GIOCATORI) {
                    // Printing all elements of a Map
                    if(!g.username.contentEquals(str)){
                        continue;
                    }
                    res+= g.username+"\nRound a cui ha partecipato: [ ";
                    for(String str2: g.paroleGiocate){
                        res+= str2+" ";    
                    }
                    res+= "]\nNumero partite giocate: "+g.partiteGiocate+"\nNumero partite vinte: "+g.partiteVinte+"\nPercentuale partite vinte: "+g.percentualeVinte+"\nStreak attuale: "+g.streakVincite+"\nStreak più lunga: "+g.maxStreakVincite+"\nGuess Distribution: "+g.guessDistribution+"\nEOL";
                    break;
                }
                this.Send(res);
            }
        }catch(Exception e){
            this.Send("UTENTECRASHATOERRORCODE");
            this.logout(USERNAME,false);
            System.err.println("ERROR showstats: "+e.getMessage());
            System.exit(1);  
        }
    }

    //3. metodo per condividere i risultati/statistiche di un giocatore tra gli altri utenti
    public void shareStats(String username) throws IOException{
        //condivido risultati ai client connessi
        String message = "";
        String Line;
        while(!message.contentEquals("EOF")){
            Line = this.Receive();
            if(Line.contentEquals("EOF")){
                break;
            }else{
                message = message + Line + "\n"; 
            }
        }
        this.sendMessageSocial(message);
    }

    //metodo per inviare messaggio sul gruppo  multicast a tutti i clienti connessi
    public void sendMessageSocial(String message) throws IOException{
        //MANDO MESSAGGIO AI CLIENT
        try {
            byte[] DataByte = message.getBytes();
            DatagramPacket packetToSend = new DatagramPacket(DataByte, DataByte.length,MULTICAST_GROUP, MULTICAST_PORT);
            MULTICAST_SOCKET.send(packetToSend);            
        } catch (Exception e) {
            System.err.println("ERROR sendmessagesocial: "+e.getMessage());
            System.exit(1); 
        }
    }

    //metodo che controlla se è stata aggiunta una nuova parola secret words nella lista concorrente dal server
    public int checkSecretWord(){
        int n = SECRET_WORDS.size() -1;
        if (n > WORDS_LENGTH){
            SECRET_WORD = SECRET_WORDS.get(n);
            WORDS_LENGTH = n;
            return 1;
        }
        return 0;
    }

    //metodo per controllare se il giocatore ha gia partecipato alla sessione della secret word attuale
    public int checkPlayer(String username){
        //CONTROLLO GIOCATORE 
        for (Giocatore giocatore: DB_STATS_GIOCATORI){
            if(giocatore.username.equals(username)){
                //controllando id e non parola garantisco che un player possa giocare anche se viene estratta
                if (giocatore.ultimoIdGiocato == GAME_ID.get()){
                    Send(Integer.toString(-1));
                    return -1;
                }else{
                    //player può giocare
                    ps(USERNAME+" OK");
                    GAME_STARTED=true;
                    TEMP_GAME_ID = GAME_ID.get();
                    TEMP_WORD_ID = SECRET_WORDS.size();
                    giocatore.ultimoIdGiocato = TEMP_GAME_ID;
                    break;
                }
            }
        }
        Send(Integer.toString(1));
        Send(Integer.toString(GAME_ID.get()));
        return 1;
    }

    //metodo per controllare e confrontare la Guessed Word con la Secret Word
    public int checkWord(String word) throws IOException{
        int res = -1;
        int lettereTrovate = 0;
        String suggerimento = "";
        String ERROR_CODE = "PAROLANONPRESENTEERRORCODE";
        
        res = this.BinarySearch(word);
        if(res == -1){
            //Parola inserita non presente nel vocabolario e mando codice errore 
            this.Send(ERROR_CODE);
            return -1;
        }
        //da modificare:
        //controllo parola -> se solo una lettera è giusta ma nella parola
        //compare piu volte quella lettera
        for(int i = 0; i<10; i++){
            if(word.charAt(i) == SECRET_WORD.charAt(i)){
                suggerimento += "[V]";
                lettereTrovate++;
            }else if(SECRET_WORD.indexOf(word.charAt(i))!= -1){
                suggerimento += "[?]";
                lettereTrovate++;
            }else{
                suggerimento += "[X]";
            }
        }
        this.Send(Integer.toString(lettereTrovate));
        this.Send(suggerimento);
        if (suggerimento.contentEquals("[V][V][V][V][V][V][V][V][V][V]")){
            ps("GIOCATORE HA INDOVINATO LA PAROLA");
            return 10;
        }
        return 1;
    }

    //API: metodi utilizzati da altre funzioni
    //metodo per aggiornare le statistiche del giocatore
    public void updateStats(String username, boolean indovinata){
        GAME_STARTED = false; //round di gioco terminato
        //Giocatore sta indovinando parola della sessione: "+TEMP_WORD_ID+"\nSessione attuale: "+GAME_ID
        ArrayList<String> words;
        String PLAYED_WORD = SECRET_WORDS.get(TEMP_WORD_ID - 1);
        
        for(Giocatore  g: DB_STATS_GIOCATORI){
            if(g.username.contentEquals(username)){
                g.partiteGiocate++;
                if(indovinata){
                    g.partiteVinte++;
                    g.streakVincite++;
                    if(g.streakVincite>g.maxStreakVincite){
                        g.maxStreakVincite = g.streakVincite;
                    }
                }else{g.streakVincite = 0;}
                g.percentualeVinte = (g.partiteVinte*100)/g.partiteGiocate;
                words = DB_PAROLE_GIOCATE.get(username);
                words.add(PLAYED_WORD);
                g.paroleGiocate = words;
                g.ultimoIdGiocato = TEMP_GAME_ID;
            }
        }
        this.updateJson();
    }

    //metodo per aggiornare il json quando modifichiamo le statistiche
    public void updateJson(){
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty print JSON
        int i = DB_STATS_GIOCATORI.size();
        //ricreo il json in formato stringa per aggiornare le stats
        String jsonStr = "[{\"gameId\" :"+GAME_ID.get()+"},";
        for (Giocatore gct: DB_STATS_GIOCATORI){
            String cont = gson.toJson(gct, Giocatore.class);
            if(i == 1){
                jsonStr = jsonStr+cont+"\n]";
            }else{
                jsonStr = jsonStr+cont+","+"\n";
                i--;
            }
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(FILE_NAME_DB))) {
            out.write(jsonStr);
        } catch (Exception e) {
            System.err.println("ERROR updatejson: "+e.getMessage());
            System.exit(1); 
        }     
    }


    //metodo per cercare se la parola inserita dal giocatore è presente nel vocabolario, si sfrutta una ricerca binaria per avere complessita bassa (nlogn)
    public int BinarySearch(String word) throws IOException {
        //utilizzo della ricerca binaria, si divide sempre in due lo spazio di ricerca e si va a dx o sx in base al confronto della parola con il risultato
        //implementata utilizzando un metodo iterativo e non ricorsivo
        RandomAccessFile file = null;
        int res = -1;
        long left = 0, right = 0, mid;
        int wordsize = 11;
        try {
            file = new RandomAccessFile(FILE_NAME_WORDS, "r");
        } catch (IOException e) {
            System.err.println("ERROR binarysearch open file: "+e.getMessage());
            System.exit(1); 
        }
        right = (file.length()/(wordsize-1));
		while (left <=  right) {
			mid = (left + right) / 2;
            //aggiorno con seed il puntatore alla posizione nel file
			file.seek(mid * wordsize); // jump to this line in the file
            String Line = new String(file.readLine());
            ps("Line: "+Line);
            int compare = word.compareTo(Line);
            if (word.contentEquals(Line)) {
                return 1;
            }else if (compare > 0) {
                left = mid + 1;            
            }else
                right = mid - 1;
		}
        try {
            file.close();
        } catch (IOException e) {
            System.err.println("ERROR binarysearch close file: "+e.getMessage());
            System.exit(1); 
        }
        return res;
	}

    public void ps(String s){
        System.out.println(s);
    }

    public void Send(String str){
        //server invia la parola al client
        try {
            dataOut.println(str);
        } catch (Exception e) {
            System.err.println("ERROR send string: "+e.getMessage());
            System.exit(1); 
        }
    }

    public String Receive(){
        //client riceve parola dal server
        String str = "";
        try {
            str = dataIn.nextLine();
        } catch (Exception e) {
            System.err.println("ERROR receive string: "+e.getMessage());
            System.exit(1); 
        }  
        return str;
    }
}