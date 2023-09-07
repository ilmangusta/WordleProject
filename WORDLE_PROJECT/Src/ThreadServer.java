package Src;

//package Src;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
//import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


// da gestiree: se un utente crasha con ctrl c e prova ad effettuare nuovamente accesso risulta gia loggato
// ricevere segnale sig ctrl c e rimuovere utente dalla sessione



public class ThreadServer implements Runnable {

    //private boolean aggiornaparola = false;
    private BufferedReader dataIn; //scanner per input
    private PrintWriter dataOut; //canale per output
    private String USERNAME; 
    private String PASSWORD;
    private String SECRET_WORD; // secret words estratta da indovinare
    private AtomicInteger GAME_ID; // identificativo della sessione di gioco di wordle (numero intero)
    private int TEMP_GAME_ID; // id temporaneo per ricordarsi l id del game a cui il giocatore sta partecipando
    private int LAST_PLAYED_GAME_ID; // id temporaneo per ricordarsi la parola che il giocatore sta cercando di giocare

    private int PLAYER_ID; //id per ricordare il giocatore
    private int TENTATIVO; //per memorizzare il tentativo e aggiornare guess distribution
    private String FILE_NAME_WORDS; 
    private Boolean GAME_STARTED = false; //variabile per controllare se un utente è in sessione di indovinare la parola
    private List<String> SECRET_WORDS = Collections.synchronizedList(new ArrayList<String>()); // una lista sincronizzata per ricordare tra i thread le parole che vengono estratte
    private List<String> DB_UTENTI_CONNESSI;  // una lista sincronizzata per ricordare chi è connesso
    private List<Giocatore> DB_STATS_GIOCATORI; //una lista sincronizzata di giocatori, utilizzata per l'aggiornamento delle statistiche dei giocatori
    private ConcurrentHashMap<String,String> DB_UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema coppia valori username - password
    private Socket SOCKET; //socket che sarà utilizzato per la conunicazione
    private MulticastSocket MULTICAST_SOCKET; //socket che sarà utilizzato per la comunicazione di pacchetti con connessione UDP
    private int MULTICAST_PORT; // porta per la comunicazione del gruppo multicast
    private InetAddress MULTICAST_GROUP; // indirizzo per la comunicazione

    public ThreadServer(Socket s, MulticastSocket mc_socket, int mc_port, InetAddress mc_group, int player_id, AtomicInteger game_id, List<Giocatore> dbGiocatori, ConcurrentHashMap<String,String> dbUtentiRegistrati, List<String> DBConnessi, List<String> secret_words, String file_name_words){
        SOCKET = s;
        MULTICAST_SOCKET = mc_socket;
        MULTICAST_PORT = mc_port;
        MULTICAST_GROUP = mc_group;
        PLAYER_ID = player_id;
        GAME_ID = game_id;
        TEMP_GAME_ID = GAME_ID.get();
        DB_STATS_GIOCATORI = dbGiocatori;
        DB_UTENTI_REGISTRATI = dbUtentiRegistrati;
        DB_UTENTI_CONNESSI = DBConnessi;
        SECRET_WORDS = secret_words;
        SECRET_WORD = secret_words.get(secret_words.size() - 1);
        FILE_NAME_WORDS = file_name_words;
        this.createCommunication();
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
        } catch (IOException e) {
            System.err.printf("ERROR createcommunication: "+ e.getMessage());
            System.exit(1);
        }
    }

    //metodo utilizzato per chiudere la connessione
    public void closeConnection(){
        try {
            if(!SOCKET.isClosed() & SOCKET != null){
                dataIn.close();
                dataOut.close(); 
                SOCKET.close();
            }
        } catch (Exception e) {
            System.err.printf("ERROR closeconneciton: "+ e.getMessage());
            System.exit(1);            
        }
    }

    //metodo che fa partire l'esecuzione del thread con le due principali attivita registrazione o login
    public void run(){ 
        //thread run partono con id diversi ognuno indipendente connesso col proprio id client (Giocatore)
        int n = 0;
        String line = "";
        try {
            dataOut.println(PLAYER_ID);
            while(true){ 
                //ciclo infinito per mandare messaggi al client e ricevere messaggi
                line =this.Receive();
                //controllo se è stata estratta una nuova parola
                checkSecretWord();
                if(line.contentEquals("Registra") || line.contentEquals("Accedi")){
                    USERNAME = this.Receive();
                    PASSWORD = this.Receive();
                    if(line.contentEquals("Registra")){
                        n = this.register();
                        this.Send(Integer.toString(n));
                    }else if(line.contentEquals("Accedi")){
                        n = this.login();
                        this.Send(Integer.toString(n));
                        if (n == 1){
                            this.startSession();
                        }
                    }
                }else if(line.contentEquals("CHIUDITERMINALE")){
                    this.closeConnection();
                    break;
                    //fine processo thread collegato col client
                }
            }
        } catch (Exception e) {
            //catturo eccezione e termino il thread collegato al client
            System.err.printf("ERROR thread client: "+ e.getMessage());
        }
        //aggiorno il json con nuove info partite e game id
        //this.updateJson();
        ps("\nTERMINATO THREAD DEL CLIENT "+PLAYER_ID);
    }

    //servizio registrazione utente al gioco 
    public int register(){
        if(PASSWORD.length()<2){
            return -2;
        }
        //sfrutto l'atomicita della operazione putifabsent per evitare la race condition con altri thread
        String value = DB_UTENTI_REGISTRATI.putIfAbsent(USERNAME, PASSWORD); 
        //value is null if put is okay OR value is now value
        if (value != null){
            ps("GIOCATORE GIA' REGISTRATO!");
            return -1;
        }else{
            int[] gd = new int[12];
            for (int i = 0; i < 12; i++){
                gd[i] = 0;
            }
            DB_STATS_GIOCATORI.add(new Giocatore(USERNAME,PASSWORD,new ArrayList<String>(),0,0,0,0,0,gd,0));
            ps("GIOCATORE REGISTRATO CON SUCCESSO! LISTA GIOCATORI REGISTRATI:\n"+DB_UTENTI_REGISTRATI);
        }
        return 1;
    }

    //servizio di login utente al gioco 
    public int login(){
        if(!DB_UTENTI_REGISTRATI.containsKey(USERNAME)){ //se username non presente: errore
            ps("GIOCATORE NON REGISTRATO");
            return -1;
        }else if(!PASSWORD.contentEquals(DB_UTENTI_REGISTRATI.get(USERNAME))){ //se username esiste ma password diversa: errore
            ps("PASSWORD NON CORRETTA");
            return -2;
        }else if(DB_UTENTI_CONNESSI.contains(USERNAME)){// se username esiste password corretta ma username gia in sessione: errore
            ps("UTENTE GIA' PRESENTE IN SESSIONE");
            return -3;
        }
        DB_UTENTI_CONNESSI.add(USERNAME);
        ps("GIOCATORE ESEGUITO ACCESSO CON SUCCESSO. LISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
        return 1;
    }

    //servizio di logout giocatore dal gioco
    public void logout(boolean indovinata, boolean crash) {
        //gestisco con un unico metodo le diverse situazioni di logout: in sessione - non in sessione - crash
        if(DB_UTENTI_CONNESSI.contains(USERNAME)){
            if(GAME_STARTED){
                ps("AGGIORNO STATISTICHE GIOCATORE: "+USERNAME);
                this.updateStats(indovinata);
                if(crash){
                    DB_UTENTI_CONNESSI.remove(USERNAME);
                    ps("DISCONNESSIONE UTENTE: "+USERNAME+".\nLISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);  
                }
            }else{
                DB_UTENTI_CONNESSI.remove(USERNAME);
                ps("DISCONNESSIONE UTENTE: "+USERNAME+".\nLISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
            }

        }else{
            ps("GIOCATORE NON PRESENTE IN SESSIONE!");
        }
        GAME_STARTED = false;
    }

    //metodo che descrive le principali operazioni effettuate dopo che l'utente effettua il login
    public void startSession(){
        String line = "";
        while(true){ 
            //ciclo infinito per mandare messaggi al client e ricevere messaggi
            try {
                line = this.Receive();
            } catch (Exception e) {
                System.err.println("ERROR startsession: Termino sessione utente: "+ e.getMessage());
            }
            checkSecretWord();
            if(line.contentEquals("gioca")){ //1. play wordle
                int n = this.checkPlayer();
                if (n == 1){
                    this.startGame();
                }
            }else if(line.contentEquals("showStats")){ //2. send me statistics
                this.showStats(); 
            }else if(line.contentEquals("shareStats")){ //3. share
                this.shareStats();
            }
            //VERSIONE ALTERNATIVA PER STAMPARE TUTTE LE STATS DEI GIOCATORI
            //else if(line.contentEquals("statsPlayers")){ //4. show me sharing
            //    this.showStats("Giocatori");
            //}
            else if(line.contentEquals("esci")){ //5. exit
                //se esco torno alla selezione di login e registrazione
                this.logout(false, false);
                return;
            }else if(line.contentEquals("CRASH_ERROR_CODE")){ //5. exit con crash
                this.logout(false, true);
                this.closeConnection();
                return;
            }            
        }
    }


    //metodo che descrive le 2 principali operazioni effettuate durante la sessione di gioco
    public void startGame(){
        TENTATIVO = 0;
        String line = "";
        while(TENTATIVO < 13){ 
            //ciclo per numero dei tentativi finche non vince - abbandona - perde per mandare messaggi al client e ricevere messaggi
            try {
                line = this.Receive();
            } catch (Exception e) {
               System.err.println("ERROR startgame: Termino sessione utente: "+ e.getMessage());
            }
            if (checkSecretWord() == 1){ 
                //se estratta nuova parola mando codice errore e termino sessione gioco
                String ERROR_CODE = "NEW_WORD_ERROR_CODE";
                this.Send(ERROR_CODE);
                this.logout(false, false);
                return;
            }
            if(line.contentEquals("esci")){
                //se ricevo esci col game iniziato non disconnetto il giocatore e torno alla selezione di menu del gioco
                //ps("richiesta logout");
                this.logout(false, false);
                return;
            }else if(line.contentEquals("CRASH_ERROR_CODE")){
                //se ricevo esci crash aggiorno stats
                this.logout(false, true);
                return;
            }else{
                int n = this.checkWord(line);
                if (n == -1){
                    ps("Parola non presente nel db parole");
                    continue;
                }
                TENTATIVO++;
                if (n == 10){
                    //se indovina la parola termino sessione gioco e aggiorno statistiche
                    this.logout(true, false);
                    return;
                }
                if (TENTATIVO == 12){
                    //se tentativi finiti e non indovinata termino sessione e aggiorno statisitche
                    this.logout(false, false);
                    return;
                }
            } 
        }

    }


    //2 metodo per mostrare le statistiche del giocatore
    public void showStats(){
        String res = "";
        //compongo il messaggio delle statistiche del giocatore da mandare
        res+= "STATISTICHE DEL GIOCATORE: ";
        for (Giocatore g: DB_STATS_GIOCATORI) {
            //stampo tutte i valori della map
            if(!g.username.contentEquals(USERNAME)){
                continue;
            }
            res+= g.username+"\n[PLAYED] Numero partite giocate: "+g.partiteGiocate+"\n[WON] Numero partite vinte: "+g.partiteVinte+"\n[%WON] Percentuale partite vinte: "+g.percentualeVinte+"\n[STREAK] Streak attuale: "+g.streakVincite+"\n[MAX STREAX] Streak più lunga consecutiva: "+g.maxStreakVincite+"\n[GUESS DISTRIBUTION] Tentativi per round: "+g.GDtoString()+"\nEOF";
            break;
        }
        try {
            this.Send(res);
        } catch (Exception e) {
            System.err.println("ERROR showstats: "+ e.getMessage());
        }
    }

    //2.2 metodo per mostrare le statistiche del giocatore NON RICHIESTO
    public void showStats2(String mode){
        String res = "";
        //VERSIONE ALTERNATIVA NON RICHIESTA DOVE POTEVO VEDERE LE STATS DEGLI ALTRI GIOCATORI
        if(mode.contentEquals("Giocatori")){
            for (Giocatore g: DB_STATS_GIOCATORI) {
                if(g.username.equals(USERNAME)){
                    continue;
                }
                res+= "STATISTICHE DEL GIOCATORE: "+g.username+"\nNumero partite giocate: "+g.partiteGiocate+"\nNumero partite vinte: "+g.partiteVinte+"\nPercentuale partite vinte: "+g.percentualeVinte+"\nStreak attuale: "+g.streakVincite+"\nStreak più lunga: "+g.maxStreakVincite+"\nGuess Distribution: "+g.guessDistribution+"\n\n";
            }
            res+= "EOF";
            this.Send(res);
        }else{
        //compongo il messaggio delle statistiche del giocatore da mandare
            res+= "STATISTICHE DEL GIOCATORE: ";
            for (Giocatore g: DB_STATS_GIOCATORI) {
                //stampo tutte i valori della map
                if(!g.username.contentEquals(USERNAME)){
                    continue;
                }
                // VERSIONE ALTERNATIVA NON RICHIESTA DOVE STAMPO TUTTE LE PAROLE A CUI HA PARTECIPATO IL GIOCATORE
                res+= g.username+"\nRound a cui ha partecipato: [ ";
                for(String str2: g.paroleGiocate){
                    res+= str2+" ";    
                }
                res+= g.username+"]\n[PLAYED] Numero partite giocate: "+g.partiteGiocate+"\n[WON] Numero partite vinte: "+g.partiteVinte+"\n[%WON] Percentuale partite vinte: "+g.percentualeVinte+"\n[STREAK] Streak attuale: "+g.streakVincite+"\n[MAX STREAX] Streak più lunga consecutiva: "+g.maxStreakVincite+"\n[GUESS DISTRIBUTION] Tentativi per round: "+g.GDtoString()+"\nEOF";
                break;
            }
            try {
                this.Send(res);
            } catch (Exception e) {
                System.err.println("ERROR showstats: "+ e.getMessage());
            }
        }
    }

    //3. metodo per condividere i risultati/statistiche di un giocatore tra gli altri utenti
    public void shareStats(){
        //condivido risultati ai client connessi
        String message = "";
        String line = "";
        try {
            while(!(line = this.Receive()).contentEquals("EOF")){
                message = message + line + "\n"; 
            }
        } catch (Exception e) {
            System.err.println("ERROR sharestats: "+ e.getMessage());
        }
        this.sendMessageSocial(message);
        this.Send("OK_SHARE");
    }

    //metodo per inviare messaggio sul gruppo  multicast a tutti i clienti connessi
    public void sendMessageSocial(String message){
        //MANDO MESSAGGIO AI CLIENT
        try {
            //creo il pacchetto per mandare il messaggio tramite canale udp
            byte[] DataByte = message.getBytes(); //converto il messaggio stringa in formato byte per il pacchetto
            DatagramPacket packetToSend = new DatagramPacket(DataByte, DataByte.length,MULTICAST_GROUP, MULTICAST_PORT);
            MULTICAST_SOCKET.send(packetToSend);            
        } catch (Exception e) {
            System.err.println("ERROR sendmessagesocial: "+ e.getMessage());
            System.exit(1);            
        }
    }

    //metodo che controlla se è stata aggiunta una nuova parola secret words nella lista concorrente dal server
    public int checkSecretWord(){
        //controllo la lunghezza delle parole giocate per controllare se è stata estratta una nuova parola
        if(GAME_ID.get() > TEMP_GAME_ID){
            SECRET_WORD = SECRET_WORDS.get(SECRET_WORDS.size() - 1);
            //ps("Estratta nuova parola: "+SECRET_WORD);
            TEMP_GAME_ID = GAME_ID.get();
            return 1;       
        }
        return 0;
    }

    //metodo per controllare se il giocatore ha gia partecipato alla sessione della secret word attuale
    public int checkPlayer(){
        //CONTROLLO GIOCATORE 
        for (Giocatore giocatore: DB_STATS_GIOCATORI){
            if(giocatore.username.equals(USERNAME)){
                //controllando id e non parola garantisco che un player possa giocare anche se viene estratta la stessa parola piu volte
                if (giocatore.ultimoIdGiocato == TEMP_GAME_ID){
                    Send(Integer.toString(-1));
                    //posso mandare quanto manca alla prossima parola ? 
                    return -1;
                }else{
                    //player può giocare
                    ps(USERNAME+" OK");
                    GAME_STARTED=true;
                    LAST_PLAYED_GAME_ID = SECRET_WORDS.size() - 1;
                    giocatore.ultimoIdGiocato = TEMP_GAME_ID;
                    break;
                }
            }
        }
        //mando codice successo e round del gioco attuale
        Send(Integer.toString(1));
        Send(Integer.toString(GAME_ID.get()));
        return 1;
    }

    //metodo per controllare e confrontare la Guessed Word con la Secret Word
    public int checkWord(String word){
        int res = -1;
        int lettereTrovate = 0;
        int lettereIndovinate = 0;
        String UDPSuggest = "";
        String UserSuggest = "";
        String green = "\u001B[32m";
        String yellow = "\u001B[33m";
        String white = "\u001B[37m";
        String ERROR_CODE = "WORD_NOT_IN_ERROR_CODE";
        //controllo con una ricerca binaria se la parola è presente
        res = this.BinarySearch(word);
        if(res == -1){
            //Parola inserita non presente nel vocabolario e mando codice errore 
            this.Send(ERROR_CODE);
            return -1;
        }
        for(int i = 0; i<10; i++){
            if(word.charAt(i) == SECRET_WORD.charAt(i)){
                UDPSuggest = UDPSuggest + green+"[V]";
                UserSuggest = UserSuggest + green + word.charAt(i);
                lettereIndovinate++;
                lettereTrovate++;
            }else if(SECRET_WORD.indexOf(word.charAt(i))!= -1){
                UDPSuggest = UDPSuggest + yellow+"[?]";
                UserSuggest = UserSuggest + yellow + word.charAt(i);
                lettereTrovate++;
            }else{
                UDPSuggest = UDPSuggest + white +"[X]";
                UserSuggest = UserSuggest + white + word.charAt(i);
            }
        }
        UDPSuggest = UDPSuggest + white; 
        UserSuggest = UserSuggest + white; 
        this.Send(Integer.toString(lettereTrovate));
        this.Send(UserSuggest);
        this.Send(UDPSuggest);
        if (lettereIndovinate == 10){
            ps("GIOCATORE HA INDOVINATO LA PAROLA");
            return 10;
        }
        return 1;
    }

    //API: metodi utilizzati da altre funzioni
    //metodo per aggiornare le statistiche del giocatore
    public void updateStats(boolean indovinata){
        GAME_STARTED = false; //round di gioco terminato
        //Giocatore sta indovinando parola della sessione: "+LAST_PLAYED_GAME_ID+"\nSessione attuale: "+GAME_ID
        String PLAYED_WORD = SECRET_WORDS.get(LAST_PLAYED_GAME_ID);
        
        for(Giocatore player: DB_STATS_GIOCATORI){
            if(player.username.contentEquals(USERNAME)){
                player.partiteGiocate++;
                if(indovinata){
                    player.partiteVinte++;
                    player.streakVincite++;
                    if(player.streakVincite>player.maxStreakVincite){
                        player.maxStreakVincite = player.streakVincite;
                    }
                    player.IncrementAttempt(TENTATIVO-1);
                }else{player.streakVincite = 0;}
                player.percentualeVinte = (player.partiteVinte*100)/player.partiteGiocate;
                player.paroleGiocate.add(PLAYED_WORD);
            }
        }
        //this.updateJson();
    }


    //metodo per cercare se la parola inserita dal giocatore è presente nel vocabolario, si sfrutta una ricerca binaria per avere complessita bassa (nlogn)
    public int BinarySearch(String word){
        //utilizzo della ricerca binaria, si divide sempre in due lo spazio di ricerca e si va a dx o sx in base al confronto della parola con il risultato
        //implementata utilizzando un metodo iterativo e non ricorsivo
        RandomAccessFile file = null;
        int res = -1;
        long left = 0, right = 0, mid;
        int wordsize = 11;
        try {
            file = new RandomAccessFile(FILE_NAME_WORDS, "r");
        } catch (IOException e) {
            System.err.println("ERROR binarysearch open file: "+ e.getMessage());
            System.exit(1); 
        }
        //right è il numero massimo di righe di parole presenti - left = 0
        try{
            right = (file.length()/(wordsize));
            while (left <=  right) {
                mid = (left + right) / 2;
                //controllo a meta tra right e left = middle e confronto 
                //aggiorno con seek il puntatore alla posizione nel file
                file.seek(mid * (wordsize)); // salto a questa riga dove trovo la parola 
                String line = new String(file.readLine());
                int compare = word.compareTo(line);
                //se minore allora rimango la prima meta tra 0 - middle 
                //se maggior rimango nell'altra meta middle - right
                if (word.contentEquals(line)) {
                    return 1;
                }else if (compare > 0) {
                    left = mid + 1;            
                }else{
                    right = mid - 1;
                }
            }
        }catch(Exception e){
            System.err.println("ERROR binarysearch IO: "+ e.getMessage());
            System.exit(1);            
        }
        try {
            file.close();
        } catch (IOException e) {
            System.err.println("ERROR binarysearch close file: "+ e.getMessage());
            System.exit(1);            
        }
        return res;
	}

    public void ps(String s){
        System.out.println(s);
    }
 
    public void Send(String str){
        //server invia la parola al client
        dataOut.println(str);
    }

    public String Receive() throws IOException{
        //client riceve parola dal server
        String str = "";
        //str = dataIn.nextline();
        str = dataIn.readLine();
        return str;
    }
}