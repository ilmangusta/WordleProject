//package ProjectLab;

import java.io.*;
import java.io.IOException;
import java.net.*;
import java.util.Properties;
import java.util.Scanner;
//import java.util.Calendar;


// how to parse string to int?

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
    public WordleClientMain(){

        ps("------ CLIENT SIDE ------");
        try {
            
            Properties props = new Properties();
            InputStream in = new FileInputStream("config.properties");
            props.load(in);
            //in.close();
            SOCKET_PORT=Integer.parseInt(props.getProperty("port"));

            
            SOCKET=new Socket("localhost", SOCKET_PORT);
            // >>>> connected to socket >>> inizio a leggere
            //dataIn=new DataInputStream((socket.getInputStream()));
            //dataOut=new DataOutputStream((socket.getOutputStream()));
            //dataIn=(socket.getInputStream());
            //dataOut=(socket.getOutputStream());
            dataIn=new Scanner(SOCKET.getInputStream());
            dataOut=new PrintWriter(SOCKET.getOutputStream(),true);
            PLAYER_ID=dataIn.nextLine();
            // ottengo player id
            //ps("CONNECTED TO SERVER PLAYER ID: "+PLAYER_ID); 
            ps("CONNECTED SUCCESFULLY"); 

        } catch (IOException e) {
            ps("Error IO [42]: "+e.getMessage());
        }
    }

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

    public void logout(){
        //disconnessione utente dalla sessione di gioco
        ps("DISCONNESSIONE SESSIONE UTENTE: "+USERNAME);
        this.Send("esci");
        System.exit(0);
    }

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
    
    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }

    public static void main(String[] args){

        WordleClientMain client=new WordleClientMain();
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
                n=client.register(USERNAME, PASSWORD);
            }else if(line.toLowerCase().contentEquals("accesso")){

                ps("Inserisci il nome utente");
                USERNAME=stdin.nextLine();
                ps("Inserisci la password");
                PASSWORD=stdin.nextLine();
                n=client.login(USERNAME,PASSWORD);
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
                client.playWORDLE();
            }else if(line.toLowerCase().contentEquals("statistiche")){
                client.sendMeStatistics();
            }else if(line.toLowerCase().contentEquals("condividi")){
                client.share();
            }else if(line.toLowerCase().contentEquals("msg")){
                //msg=mostraStatisticheGiocatori
                client.showMeSharing();
            }else if(line.toLowerCase().contentEquals("giocatori")){
                client.showPlayersLogged();
            }else if(line.toLowerCase().contentEquals("aggiorna")){
                //client.updateDate();
            }else if(line.toLowerCase().contentEquals("esci")){
                client.logout();
            }
        }
    }
}
