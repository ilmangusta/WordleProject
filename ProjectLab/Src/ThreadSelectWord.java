package Src;

//package Src;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadSelectWord implements Runnable{

    private AtomicInteger GAME_ID; //identificativo del game in corso
    private String FILE_NAME_WORDS; //nome del file dove recuperare le parole da estrarre
    private String SECRET_WORD;
    private int PERIOD; //periodo in minuti tra la pubblicazione di una parola e la prossima 

    public ThreadSelectWord(String file_name, String secret_word, AtomicInteger game_id, int period){
        FILE_NAME_WORDS = file_name;
        SECRET_WORD = secret_word;
        GAME_ID = game_id;
        PERIOD = period;
    }

    @Override
    public void run() {
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
                        ps("ERROR : "+e.getMessage());
                    }     
                    GAME_ID.incrementAndGet();
                    SECRET_WORD = line;
                    ps("NUOVA PAROLA DELLA GIORNATA: "+ line+" - GAME_ID: "+GAME_ID);
                    try {
                        br.close();
                    } catch (Exception e) {
                        ps("ERROR closing file: "+e.getMessage());
                    }
                } catch (IOException e) {
                    ps("ERROR processingfile: "+e.getMessage());
                }
            }
        },0,PERIOD*60*1000);
    }

    /*
    //metodo per aggiornare il json quando modifichiamo le statistiche
    public void updateJson(){
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty print JSON
        int i = DB_STATS_GIOCATORI.size();
        //ricreo il json in formato stringa per aggiornare le stats

        String jsonStr = "[{'gameId' : 3},";
        for (Giocatore gct: DB_STATS_GIOCATORI){
            String cont = gson.toJson(gct, Giocatore.class);
            if(i == 1){
                jsonStr = jsonStr+cont+"\n]";
            }else{
                jsonStr = jsonStr+cont+","+"\n";
                i--;
            }
        }
        try (PrintWriter out = new PrintWriter(new FileWriter("./DBGiocatoriRegistrati.json"))) {
            out.write(jsonStr);
        } catch (Exception e) {
            ps("ERROR updatejson: "+e.getMessage());
        }     
    }
    */


    //metodo per stampare messaggi, solo per comodità
    public static void ps(String s){
        System.out.println(s);
    }
}
