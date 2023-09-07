package Src;

//package Src;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//importo libreria gson per utilizzare reader
import com.google.gson.stream.JsonReader;

//Creo un database dei giocatori registrati presenti nel file json in una arraylist con tutti i giocatori
// e le loro informazioni e statistiche di gioco. Creo anche una hashmap per mappare le credenziali di tutti gli utenti registrati
// e una hashmap per tutte le parole indovinate dai player per controllare se ha gia partecipato ad un round

public class Database {

    public String FILE_NAME_DB; //nome del file json contenente le informazioni dei giocatori
    public List<Giocatore> STATS_GIOCATORI  =  Collections.synchronizedList(new ArrayList<Giocatore>()); // una lista di giocatori per le statistiche
    public ConcurrentHashMap<String,String> UTENTI_REGISTRATI = new ConcurrentHashMap<String,String>(); // una hashmap coppie username - password per il login
    public AtomicInteger GAME_ID;

    Database(String file_name, AtomicInteger game_id){
        FILE_NAME_DB = file_name;
        GAME_ID = game_id;
        this.setUpDatabase();
    }

    public void setUpDatabase(){
        String user = "";
        String password = "";
        int partiteGiocate = 0;
        ArrayList<String> paroleGiocate = null;
        int partiteVinte = 0;
        float percentualeVinte = 0;
        int streakVincite = 0;
        int maxStreakVincite = 0;
        int[] gd = null;
        int ultimoIdGiocato = 0;
        int gameid;

        try {
            String str;
            File input_json = new File(FILE_NAME_DB);
            JsonReader reader  =  new JsonReader(new FileReader(input_json));
            //apro array
            if(input_json.length() == 0){
                ps("CREO DATABASE: ID_GAME = 0");
                gameid = 0;
                return;
            }else{
                reader.beginArray();
                reader.beginObject();
                str = reader.nextName();        
                gameid = Integer.parseInt(reader.nextString());
                GAME_ID.set(gameid);
                reader.endObject();
                while (reader.hasNext()) {
                    //apro object per lettura
                    reader.beginObject();
                    while (reader.hasNext()){
                        str = reader.nextName();
                        if (str.contentEquals("username")) {
                            user = reader.nextString();
                        }else if(str.contentEquals("password")) {
                            password = reader.nextString();
                        }else if (str.contentEquals("paroleGiocate")) {
                            reader.beginArray();
                            paroleGiocate = new ArrayList<String>();
                            while(reader.hasNext()){
                                String word = reader.nextString();
                                paroleGiocate.add(word);
                            }
                            reader.endArray();                   
                        }else if(str.contentEquals("partiteGiocate")) {
                            partiteGiocate = Integer.parseInt(reader.nextString());
                        }else if(str.contentEquals("partiteVinte")) {
                            partiteVinte = Integer.parseInt(reader.nextString());
                        }else if(str.contentEquals("percentualeVinte")) {
                            percentualeVinte = Float.parseFloat(reader.nextString());
                        }else if(str.contentEquals("streakVincite")) {
                            streakVincite = Integer.parseInt(reader.nextString());
                        }else if(str.contentEquals("maxStreakVincite")) {
                            maxStreakVincite = Integer.parseInt(reader.nextString());
                        }else if(str.contentEquals("guessDistribution")) {
                            reader.beginArray();
                            int i=0;
                            gd = new int[12];
                            while(reader.hasNext()){
                                int gd_i = Integer.parseInt(reader.nextString());
                                gd[i] = gd_i;
                                i++;
                            }
                            reader.endArray();                
                        }else if(str.contentEquals("ultimoIdGiocato")) {
                            ultimoIdGiocato = Integer.parseInt(reader.nextString());
                            //aggiungo giocatore in lista giocatori per modificare il json
                            Giocatore giocatore = new Giocatore(user,password, paroleGiocate, partiteGiocate, partiteVinte, percentualeVinte,streakVincite, maxStreakVincite, gd, ultimoIdGiocato);
                            STATS_GIOCATORI.add(giocatore);
                            //aggiungo il player nella hashmap DB dei giocatori registrati
                            UTENTI_REGISTRATI.put(user,password);
                            //inserisco giocatore
                        }else{reader.nextString();}
                    }
                    reader.endObject();
                }
            }
            reader.endArray();
        } //controllo e catturo le eccezioni
        catch (FileNotFoundException e) {
            System.err.printf("ERROR filenotfound: "+e.getMessage());
            System.exit(1); 
        }catch (IOException e) {
            System.err.printf("ERROR IO error: "+e.getMessage());
            System.exit(1); 
        }catch (Exception e) {
            System.err.printf("ERROR : "+e.getMessage());
            System.exit(1); 
        }  
        ps("DATABASE CREATO CORRETTAMENTE...GIOCATORI REGISTRATI:\n"+UTENTI_REGISTRATI+"\nROUND NUMERO: "+GAME_ID.get());
    }

    //metodo per aggiornare il json quando modifichiamo le statistiche
    public void updateJson(){
        //potevo utilizzare in alternativa una scrittura passo passo bufferizata scrivendo in ogni attributo ma 
        //ma ho deciso di effettuare una scrittura completa una volta che ho tutti i dati in giocatori in stringa
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // pretty print JSON
        int i = STATS_GIOCATORI.size();
        //ricreo il json in formato stringa per aggiornare le stats
        String jsonStr = "[{\"gameId\" :"+GAME_ID.get()+"},";
        for (Giocatore player: STATS_GIOCATORI){
            String cont = gson.toJson(player, Giocatore.class);
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
            System.err.println("ERROR updatejson: "+ e.getMessage());
            System.exit(1);            
        }     
    }

    public static void ps(String s){
        System.out.println(s);
    }

}
