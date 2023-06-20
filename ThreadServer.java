//package ProjectLab;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ThreadServer implements Runnable {

    //private boolean aggiornaparola=false;
    private Scanner dataIn; //scanner per input
    private PrintWriter dataOut; //canale per output
    private String LINE=""; //"buffer" per comunicazione
    private String USERNAME; 
    private String PASSWORD;
    private String SECRET_WORD;
    private String FILE_NAME_WORDS; 
    private ArrayList<Giocatore> DB_STATS_GIOCATORI; //una lista di giocatori, utilizzata per l'aggiornamento delle statistiche dei giocatori
    private ConcurrentHashMap<String,String> DB_UTENTI_REGISTRATI; // una hashmap concorrente per ricordare chi è registrato nel sistema coppia valori username - password
    private List<String> DB_UTENTI_CONNESSI;  // una lista sincronizzata per ricordare chi è connesso
    private ConcurrentHashMap<String,ArrayList<String>> DB_PAROLE_GIOCATE;  // una hashmap con coppia Giocatore - Tutte le parole giocate
    private Socket SOCKET; //socket che sarà utilizzato per la conunicazione
    private int PLAYER_ID; //id per ricordare il giocatore

    public ThreadServer(Socket s, int id,ArrayList<Giocatore> dbGiocatori,ConcurrentHashMap<String,ArrayList<String>> dbParole, ConcurrentHashMap<String,String> dbUtentiRegistrati, List<String> DBConnessi, String str, String file_name){
        SOCKET=s;
        PLAYER_ID=id;
        DB_STATS_GIOCATORI=dbGiocatori;
        DB_PAROLE_GIOCATE=dbParole;
        DB_UTENTI_REGISTRATI=dbUtentiRegistrati;
        DB_UTENTI_CONNESSI=DBConnessi;
        SECRET_WORD=str;
        FILE_NAME_WORDS=file_name;
        this.createCommunication();
    }

    // inizializzo canali per la comunicazione
    public void createCommunication(){

        try{
            //dataIn=new DataInputStream((socket.getInputStream()));
            //dataOut=new DataOutputStream((socket.getOutputStream()));
            //dataIn=(socket.getInputStream());
            //dataOut=(socket.getOutputStream());
            dataIn=new Scanner(SOCKET.getInputStream());
            dataOut=new PrintWriter(SOCKET.getOutputStream(),true);
        } catch (IOException e) {
            ps("Error IO [46]: "+e.getMessage());
        }
    }

    // utilizzato per chiudere i canali di comunicazione
    public void closeCommunication(){
        dataIn.close();
        dataOut.close();
    }


    //metodo per evitare di fare ctrl c sul server per spegnerlo
    public void closeServer(){
        try {
            SOCKET.close();
        }catch (Exception e){ps("Error closing server: "+e.getMessage());}
        ps("Closed server...");
    }

    public void run(){ 

        //thread run partono con id diversi ognuno indipendente connesso col proprio id client (Giocatore)
        int n=0;
        try {
            dataOut.println(PLAYER_ID);
            while(dataIn.hasNextLine()){ 
                //ciclo infinito per mandare messaggi al client e ricevere messaggi
                //ps("Turno del giocatore: "+PLAYER_ID);
                //line=dataIn.readLine(); DEPRECATO
                LINE=dataIn.nextLine();
                //ps("line: "+LINE);
                if(LINE.contentEquals("Registra") ||LINE.contentEquals("Accedi")){
                    USERNAME=dataIn.nextLine();
                    PASSWORD=dataIn.nextLine();
                    if(LINE.contentEquals("Registra")){
                        n=this.register(USERNAME,PASSWORD);
                        dataOut.println(n);
                    }else if(LINE.contentEquals("Accedi")){
                        n=this.login(USERNAME,PASSWORD);
                        dataOut.println(n);
                    }
                }else if(LINE.contentEquals("gioca")){
                    boolean giaGiocato=this.checkPlayer(USERNAME);
                    if(giaGiocato){
                        dataOut.println(2);
                    }else{
                        dataOut.println(1);
                    }
                }else if(LINE.contentEquals("NonIndovinata")){
                    this.updateStats(USERNAME,false);
                }else if(LINE.contentEquals("Indovinata")){
                    this.updateStats(USERNAME,true);
                }else if(LINE.contentEquals("mostraGiocatoriConnessi")){
                    this.mostraGiocatoriConnessi();
                }else if(LINE.contentEquals("showStats")){
                    this.showStats(USERNAME);
                }else if(LINE.contentEquals("shareStats")){
                    this.shareStats(USERNAME);
                }else if(LINE.contentEquals("showStatsGiocatori")){
                    this.showStats("Giocatori");
                }else if(LINE.contentEquals("esci")){
                    this.logout(USERNAME);
                    return;
                }else if(LINE.contentEquals("closeserver")){
                    this.logout(USERNAME);
                    this.closeServer();
                    return;
                }else{
                    ps("Parola scelta dal giocatore: "+LINE);
                    this.checkWord(LINE);
                }
            }
        } catch (Exception e) {
            ps("ERRORE CLIENT: "+e.getMessage());
            ps("Termino sessione utente per crash. Non aggiorno statistiche.");
            this.logout(USERNAME);
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
            DB_STATS_GIOCATORI.add(new Giocatore(username,password,new ArrayList<String>(),0,0,0,0,0,0));
            DB_UTENTI_REGISTRATI.put(username,password);
            DB_PAROLE_GIOCATE.put(username,new ArrayList<String>());
            this.updateJson();
            ps("GIOCATORE REGISTRATO CON SUCCESSO! LISTA GIOCATORI REGISTRATI:\n"+DB_UTENTI_REGISTRATI);
            return 1;
        }
    }

    //servizio di login giocatore al gioco 
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
    public void logout(String username){
        //if(DBUtentiConnessi.containsKey(username)){
        if(DB_UTENTI_CONNESSI.contains(username)){
            DB_UTENTI_CONNESSI.remove(username);
            ps("DISCONNESSIONE UTENTE: "+username+".\nLISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
        }else{
            ps("GIOCATORE NON PRESENTE IN SESSIONE!");
        }
        closeCommunication();
    }

    //metodo di debug per controllare lato server i giocatori connessi in un preciso istante
    public void mostraGiocatoriConnessi(){
        ps("LISTA GIOCATORI PRESENTI IN SESSIONE:\n"+DB_UTENTI_CONNESSI);
    }

    //scegli la parola giornaliera
    public String selectWord(String input) throws IOException{
        File file = new File( input);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            int fileElement=((int) file.length()) / 11;
            int cont=0;
            ps(fileElement+" elementi");
            int element=(int)((Math.random()*fileElement-1)+1);
            ps("SCELTO RIGA "+element);

            try {
                while (true){
                    if(cont>=element){
                        break;
                    }
                    SECRET_WORD = br.readLine();
                    cont++;
                }
            } catch (IOException e) {
                ps("Error IO [173]: "+e.getMessage());
            }
        }
        /* 
        int randomTime=(int)((Math.random()*2-1)+1);
        Timer T = new Timer();
        TimerTask selectWord = new TimerTask(){
            @Override
            public void run(){
                System.out.println("Orario nuova parola arrivato!");
                stop=true;
            }
        };
        
        T.schedule(selectWord, randomTime*1000);

        try {
            while (true) != null){
                Thread.sleep(50);
                //ps(SecretWord);
                if(stop){
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
        return SECRET_WORD;
    }

    //metodo per aggiornare le statistiche del giocatore
    public void updateStats(String username, boolean indovinata){
        ArrayList<String> words;
        for(Giocatore  g: DB_STATS_GIOCATORI){
            if(g.username.contentEquals(username)){
                g.partiteGiocate++;
                if(indovinata){
                    g.partiteVinte++;
                    g.streakVincite++;
                    if(g.streakVincite>g.maxStreakVincite){
                        g.maxStreakVincite=g.streakVincite;
                    }
                }else{g.streakVincite=0;}
                g.percentualeVinte=(g.partiteVinte*100)/g.partiteGiocate;
                words=DB_PAROLE_GIOCATE.get(username);
                words.add(SECRET_WORD);
                g.paroleGiocate=words;
            }
        }
        ps("AGGIORNA STATISTICHE DB PAROLE GIOCATE:\n"+DB_PAROLE_GIOCATE);
        this.updateJson();
    }

    //metodo per aggiornare il json quando modifichiamo le statistiche
    public void updateJson(){
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty print JSON
        int i=DB_STATS_GIOCATORI.size();

        String jsonStr="[";
        for (Giocatore gct: DB_STATS_GIOCATORI){
            String cont = gson.toJson(gct, Giocatore.class);
            if(i==1){
                jsonStr=jsonStr+cont+"\n]";
            }else{
                jsonStr=jsonStr+cont+","+"\n";
                i--;
            }
        }
        ps("GIOCATORI E STATISTICHE:\n"+jsonStr);
        try (PrintWriter out = new PrintWriter(new FileWriter("./DBGiocatoriRegistrati.json"))) {
            out.write(jsonStr);
        } catch (Exception e) {
            ps("Error exception [252]: "+e.getMessage());
        }     
    }

    //metodo per mostrare le statistiche sia del giocatore che in alternativa di tutti i giocatori 
    public void showStats(String str){
        String res="";
        if(str.contentEquals("Giocatori")){
            for (Giocatore g: DB_STATS_GIOCATORI) {
                if(g.username.equals(USERNAME)){
                    continue;
                }
                res+="STATISTICHE DEL GIOCATORE: "+g.username+"\nRound a cui ha partecipato: [ ";
                for(String str2: g.paroleGiocate){
                    res+=str2+"  ";    
                }
                res+="]\nNumero partite giocate: "+g.partiteGiocate+"\nNumero partite vinte: "+g.partiteVinte+"\nPercentuale partite vinte: "+g.percentualeVinte+"\nStreak attuale: "+g.streakVincite+"\nStreak più lunga: "+g.maxStreakVincite+"\nGuess Distribution: "+g.guessDistribution+"\n\n";
            }
            res+="EOL";
            dataOut.println(res);
        }else{
            res+="STATISTICHE DEL GIOCATORE: ";
            for (Giocatore g: DB_STATS_GIOCATORI) {
                // Printing all elements of a Map
                if(!g.username.contentEquals(str)){
                    continue;
                }
                res+=g.username+"\nRound a cui ha partecipato: [ ";
                for(String str2: g.paroleGiocate){
                    res+=str2+" ";    
                }
                res+="]\nNumero partite giocate: "+g.partiteGiocate+"\nNumero partite vinte: "+g.partiteVinte+"\nPercentuale partite vinte: "+g.percentualeVinte+"\nStreak attuale: "+g.streakVincite+"\nStreak più lunga: "+g.maxStreakVincite+"\nGuess Distribution: "+g.guessDistribution+"\nEOL";
                break;
            }
            dataOut.println(res);
        }
    }

    //metodo per condividere i risultati/statistiche di un giocatore tra gli altri utenti
    public void shareStats(String username){
        //da implementare
        ps("METODO DA IMPLEMENTARE");
    }

    public void sendWord(String str){
        //server invia la parola al client
        try {
            dataOut.println(str);
        } catch (Exception e) {
            ps("Error exception [304]: "+e.getMessage());
        }
    }

    //metodo per controllare se il giocatore ha gia partecipato alla sessione della secret word attuale
    public boolean checkPlayer(String username){
        boolean giagiocato=false;
        for(String s: DB_PAROLE_GIOCATE.get(username)){
            if(s.contentEquals(SECRET_WORD)){
                giagiocato=true;
                ps("player: "+username+" già partecipato alla sessione");
                return giagiocato;
            }
        }
        ps("player: "+username+" può giocare");
        return giagiocato;
    }

    //metodo per controllare e confrontare la Guessed Word con la Secret Word
    public void checkWord(String word) throws IOException{

        int res=0;
        try {
            res=this.BinarySearch(word);
        } catch (IOException e) {
            ps("Error IO Binary search: "+e.getMessage());
        }
        if(res==-1){
            ps("Parola inserita non presente nel vocabolario");
            dataOut.println("Parola non presente nel vocabolario, tentativo non contato. Riprova.");
            dataOut.println(-1);
            return;
        }
        int lettereTrovate=0;
        String res2="";
        
        //da modificare:
        //controllo parola -> se solo una lettera è giusta ma nella parola
        //compare piu volte quella lettera
        for(int i=0; i<10; i++){
            if(word.charAt(i)==SECRET_WORD.charAt(i)){
                res2+="[V]";
                lettereTrovate++;
            }else if(SECRET_WORD.indexOf(word.charAt(i))!=-1){
                res2+="[?]";
                lettereTrovate++;
            }else{
                res2+="[X]";
            }
        }
        //ps("Lettere indovinate: "+lettereIndovinate+" - Lettere trovate: "+lettereTrovate);
        sendWord(res2);
        dataOut.println(lettereTrovate);
    }


    //metodo per cercare se la parola inserita dal giocatore è presente nel vocabolario, si sfrutta una ricerca binaria per avere complessita bassa (nlogn)
    public int BinarySearch(String word) throws IOException {
        //utilizzo della ricerca binaria, si divide sempre in due lo spazio di ricerca e si va a dx o sx in base al confronto della parola con il risultato
        //implementata utilizzando un metodo iterativo e non ricorsivo
        RandomAccessFile file=null;
        int res=-1;
        long left=0, right=0, mid;
        try {
            file = new RandomAccessFile(FILE_NAME_WORDS, "r");
            right = (file.length()/12);
        } catch (IOException e) {
            ps("Error IO: "+e.getMessage());
        }
		while (left <= right) {
			mid = (left + right) / 2;
			file.seek(mid * 11);
            String line = file.readLine();
            if (word.equals(line)) {
                res=1;
                break;
            }
            if (word.compareTo(line) < 0) {
                right = mid - 1;            
            }else
                left = mid + 1;
		}
        try {
            file.close();
        } catch (IOException e) {
            ps("Error IO: "+e.getMessage());
        }
        return res;
	}

    //metodo per registrare un utente e per inserirlo nel file json persistente nel server e anche nelle strutture dati hashmap
    public int insertPlayer(String username, String psd,ArrayList<String> array,int p1, int p2, int p3, int p4, int p5, int p6){
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty print JSON

        Giocatore giocatore=new Giocatore(username, psd, array,p1,p2,p3,p4,p5,p6);
        String jsonStr="[";
        for (Giocatore gct: DB_STATS_GIOCATORI){
            String cont = gson.toJson(gct, Giocatore.class);
            jsonStr=jsonStr+cont+","+"\n";
            ps("giocatore: "+jsonStr);
        }
        jsonStr=jsonStr+gson.toJson(giocatore, Giocatore.class)+"]";
        ps("giocatori :\n"+jsonStr);

        try (PrintWriter out = new PrintWriter(new FileWriter("./DBGiocatoriRegistrati.json"))) {
            out.write(jsonStr);
        } catch (Exception e) {
            ps("Error exception [414]: "+e.getMessage());
        }
        return 1;
    }

    public static void ps(String s){
        System.out.println(s);
    }
}