//package ProjectLab;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.Scanner;
//import java.util.Calendar;

public class WordleClientMain {
    
    private Socket SOCKET;
    private Scanner dataIn;
    private PrintWriter dataOut;
    public String PLAYER_ID;
    private int TENTATIVO=1;
    private static Scanner stdin=new Scanner(System.in);
    private static String USERNAME;
    private static String PASSWORD;
    private static int SOCKET_PORT;
    private static String SOCKET_HOST;

    // costruttore wordleclientmain inizializzo canale socket 
    public WordleClientMain(){
        try {        
            SOCKET=new Socket(SOCKET_HOST, SOCKET_PORT);
            ps("CONNESSIONE AVVENUTA CON SUCCESSO"); 
            this.createCommunication();
        } catch (IOException e) {
            ps("Error IO [29]: "+e.getMessage());
        }
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
            PLAYER_ID=dataIn.nextLine();
        } catch (IOException e) {
            ps("Error IO [45]: "+e.getMessage());
        }
    }

    // utilizzato per chiudere i canali di comunicazione
    public void closeCommunication(){
        dataIn.close();
        dataOut.close();
    }

    //metodo per iniziare ad inserire stringhe ma il gioco non è ancora iniziato
    public void startClient(){
        int n=-1;
        String line;
        //Calendar c1 = Calendar.getInstance();

        while(true){
            //ps("Date: " +c1.getTime());
            ps("REGISTRAZIONE O ACCESSO?");
            line=stdin.nextLine();
            if(line.toLowerCase().contentEquals("registrazione")){
                ps("Inserisci il nome utente");
                USERNAME=stdin.nextLine();
                ps("Inserisci la password");
                PASSWORD=stdin.nextLine();
                n=this.register(USERNAME, PASSWORD);
            }else if(line.toLowerCase().contentEquals("accesso")){

                ps("Inserisci il nome utente");
                USERNAME=stdin.nextLine();
                ps("Inserisci la password");
                PASSWORD=stdin.nextLine();
                n=this.login(USERNAME,PASSWORD);
                if(n==1){
                    break;
                }
            }else{ps("SCELTA SBAGLIATA, RIPROVA.");}
        }
        while(true){
            ps("INSERISCI COMANDO.\n[HELP PER LEGENDA].");
            line=stdin.nextLine();
            if(line.toLowerCase().contentEquals("help")){
                ps("1. [GIOCA]: Manda richiesta per iniziare un nuovo round di wordle;\n2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;\n3. [CONDIVIDI]: Richiesta di condividere i risultati del gioco sul social;\n4. [MSG]: Mostra statistiche degli altri giocatori;\n5. [ESCI]: Termina la sessione di gioco");
            }else if(line.toLowerCase().contentEquals("gioca")){
                this.playWORDLE();
            }else if(line.toLowerCase().contentEquals("statistiche")){
                this.sendMeStatistics();
            }else if(line.toLowerCase().contentEquals("condividi")){
                this.share();
            }else if(line.toLowerCase().contentEquals("msg")){
                //msg=mostraStatisticheGiocatori
                this.showMeSharing();
            }else if(line.toLowerCase().contentEquals("giocatori")){
                this.showPlayersLogged();
            }else if(line.toLowerCase().contentEquals("aggiorna")){
                //client.updateDate();
            }else if(line.toLowerCase().contentEquals("esci")){
                this.logout();
                return;
            }else if(line.toLowerCase().contentEquals("closeserver")){
                this.closeServer();
                return;
            }
        }
    }

    // metodo per richiede di registrarsi nel sistema
    public int register(String username, String psw){
        int n=0;
        this.Send("Registra");
        this.Send(username);
        this.Send(psw);
        n=dataIn.nextInt();
        if(n==1){
            ps("[SUCCESS]\nREGISTRAZIONE AVVENUTA CON SUCCESSO!\n");
        }else if(n==-1){
            ps("[ERROR1]\nUSERNAME GIA' ESISTENTE!\nACCEDI COL TUO USERNAME O SCEGLI UN ALTRO.");
        }else if(n==-2){
            ps("[ERROR2]\nPASSWORD NON EFFICACE!");
        }
        return n;
    }

    // metodo per richiede di accedere al sistema
    public int login(String username, String psw){
        int n=0;
        this.Send("Accedi");
        this.Send(username);
        this.Send(psw);
        n=dataIn.nextInt();
        if(n==1){
            ps("[SUCCESS]\nACCESSO AVVENUTO CON SUCCESSO!");
        }else if(n==-1){
            ps("[ERROR1]\nUSERNAME NON ESISTENTE, REGISTRATI!");
        }else if(n==-2){
            ps("[ERROR2]\nPASSWORD SBAGLIATA, RIPROVA!");
        }else if(n==-3){
            ps("[ERROR3]\nUTENTE GIA' PRESENTE IN UN ALTRA SESSIONE!");
        }
        return n;
    }

    // metodo per richiede di uscire dal sistema
    public void logout(){
        //disconnessione utente dalla sessione di gioco
        ps("RICHIESTA DISCONNESSIONE SESSIONE UTENTE: "+USERNAME);
        this.Send("esci");
        this.closeCommunication();
    }

    // metodo per richiede di iniziare a giocare a WORDLE 
    public void playWORDLE(){
        //richiesta di iniziare la sessione di gioco 
        ps("\nRICHIESTA DI INIZARE A GIOCARE...\n");
        this.Send("gioca");
        String res="";
        while(res==""){
            res=dataIn.nextLine();
        }
        int i=Integer.parseInt(res);
        if(i==1){
            ps("RICHIESTA ANDATA A BUON FINE, INIZIO GIOCO!");
            this.sendWord();
        }else if(i==2){
            ps("GIOCATORE HA GIA' PARTEICPATO A QUESTA SESSIONE, ASPETTA LA PROSSIMA!");
        }
    }

    public void Send(String str){
        //client invia la parola al server
        try {
            dataOut.println(str);
        } catch (Exception e) {
            ps("Error exception [109]: "+e.getMessage());
        }
    }

    //inizia a mandare le parole da indovinare
    public void sendWord(){

        String line;
        boolean indovinata=false;
        ps("Inserisci la parola [GUESSED WORD].");
        while(TENTATIVO<=12){

            ps("Tentativo numero: "+TENTATIVO+"\n");
            String linefromServer="null";
            line=stdin.nextLine();
            if(line.contentEquals("esci")){
                this.logout();
            }else if(line.length()<10){
                ps("\nParola immessa troppo corta. Deve essere di 10 caratteri");
                continue;
            }
            ps("Parola inserita: "+line);
            this.Send(line);

            linefromServer=dataIn.nextLine();
            ps(linefromServer);
            if(Integer.parseInt(linefromServer=dataIn.nextLine())>-2){
                if(Integer.parseInt(linefromServer)==-1){
                    continue;
                }else if(Integer.parseInt(linefromServer)==10){
                    indovinata=true;
                    break;
                }else{
                    ps("Lettere indovinate: "+linefromServer);
                    TENTATIVO++;
                }
            }
        }
        if(!indovinata){
            ps("Non hai indovinato la Secret Word di questo round!\n");
            this.Send("NonIndovinata");
        }else{
            ps("Hai indovinato la Secret Word di questo round!\nAspetta la prossima parola estratta.\n");
            this.Send("Indovinata");
        }
        ps("Round di Wordle Terminato!\n");
        TENTATIVO=1;
        this.sendMeStatistics();
    }
    
    //richiesta di statistiche aggiornate all ultimo gioco  
    public void sendMeStatistics(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DELL'UTENTE...\n");
        this.Send("mostraStatistiche");
        String res="";
        while(true){
            res=dataIn.nextLine();
            if(res.contentEquals("EOL")){break;}
            ps(res);
        }
    }

    //richiesta di condividere le statistiche in un social (multicast);
    public void share(){
        ps("\nRICHIESTA DI CONDIVIDERE RISULTATI SUL SOCIAL...\n");
        ps("...METODO DA IMPLEMENTARE");
        this.Send("condividiStatistiche");
    }

    //mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti.
    public void showMeSharing(){
        ps("\nRICHIESTA DELLE STATISTICHE DI GIOCO DEGLI ALTRI UTENTI...\n");
        this.Send("mostraStatisticheGiocatori");
        String res="";
        while(true){
            res=dataIn.nextLine();
            if(res.contentEquals("EOL")){break;}
            ps(res);
        }
    }

    //metodo per visualizzare i giocatori loggati nel server
    public void showPlayersLogged(){
        ps("\nRICHIESTA VISUALIZZAZIONE GIOCATORI CONNESSI\n");
        Send("mostraGiocatoriConnessi");
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

    public static void main(String[] args) throws IOException{

        ps("------ CLIENT SIDE ------");
        try {
            //configuration file per ottenere tutte le informazioni utili come parametri o valori defualt 
            Properties props = new Properties();
            InputStream in = new FileInputStream("ConfigClient.properties");
            props.load(in);
            SOCKET_PORT=Integer.parseInt(props.getProperty("socket_port"));
            SOCKET_HOST=props.getProperty("socket_host");
            in.close();
            WordleClientMain CLIENT=new WordleClientMain();
            CLIENT.startClient();
        } catch (Exception e) {
            ps("Error configuration client: "+e.getMessage());
        }
    }
}
