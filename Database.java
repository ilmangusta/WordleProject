//package ProjectLab;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
//importo libreria gson per utilizzare reader
import com.google.gson.stream.JsonReader;

//Creo un database dei giocatori registrati presenti nel file json in una arraylist con tutti i giocatori
// e le loro informazioni e statistiche di gioco. Creo anche una hashmap per mappare le credenziali di tutti gli utenti registrati
// e una hashmap per tutte le parole indovinate dai player per controllare se ha gia partecipato ad un round

public class Database {

    public String FILE_NAME="./DBGiocatoriRegistrati.json";
    public ArrayList<Giocatore> STATS_GIOCATORI = new ArrayList<Giocatore>();
    public ConcurrentHashMap<String,String> UTENTI_REGISTRATI=new ConcurrentHashMap<String,String>();
    public ConcurrentHashMap<String,ArrayList<String>> PAROLE_GIOCATE=new ConcurrentHashMap<String,ArrayList<String>>();

    Database(){

        String user="";
        String password="";
        int partiteGiocate=0;
        ArrayList<String> paroleGiocate= null;
        int partiteVinte=0;
        float percentualeVinte=0;
        int streakVincite=0;
        int maxStreakVincite=0;
        int guessDistribution=0; 
    
        File input = new File(FILE_NAME);
        try {

            JsonReader reader = new JsonReader(new FileReader(input));
            //apro array
            reader.beginArray();
            while (reader.hasNext()) {
                //apro object per lettura
                reader.beginObject();
                while (reader.hasNext()) {

                    String str = reader.nextName();
                    if (str.contentEquals("username")) {
                        user = reader.nextString();
                    }else if(str.contentEquals("password")) {
                        password=reader.nextString();
                    }else if (str.contentEquals("paroleGiocate")) {
                        reader.beginArray();
                        paroleGiocate=new ArrayList<String>();
                        while(reader.hasNext()){
                            String word=reader.nextString();
                            paroleGiocate.add(word);
                        }
                        reader.endArray();                   
                    }else if(str.contentEquals("partiteGiocate")) {
                        partiteGiocate=Integer.parseInt(reader.nextString());
                    }else if(str.contentEquals("partiteVinte")) {
                        partiteVinte=Integer.parseInt(reader.nextString());
                    }else if(str.contentEquals("percentualeVinte")) {
                        percentualeVinte=Float.parseFloat(reader.nextString());
                    }else if(str.contentEquals("streakVincite")) {
                        streakVincite=Integer.parseInt(reader.nextString());
                    }else if(str.contentEquals("maxStreakVincite")) {
                        maxStreakVincite=Integer.parseInt(reader.nextString());
                    }else if(str.contentEquals("guessDistribution")) {
                        guessDistribution=Integer.parseInt(reader.nextString());
                        //aggiungo giocatore in lista giocatori per modificare il json
                        Giocatore giocatore=new Giocatore(user,password, paroleGiocate, partiteGiocate, partiteVinte, percentualeVinte,streakVincite, maxStreakVincite, guessDistribution);
                        STATS_GIOCATORI.add(giocatore);
                        //aggiungo il player nella hashmap DB dei giocatori registrati e memorizzo anche le parole a cui ha partecipato il giocatore nella hashmap DB
                        UTENTI_REGISTRATI.put(user,password);
                        PAROLE_GIOCATE.put(user,paroleGiocate); 
                        //inserisco giocatore
                    }else{reader.nextString();}
                }
                reader.endObject();
            }
            reader.endArray();
        } //controllo e catturo le eccezioni
        catch (FileNotFoundException e) {
            ps("Error file not found [82]: "+e.getMessage());
        }catch (IOException e) {
            ps("Error IO [84]: "+e.getMessage());
        }catch (Exception e) {
            ps("Exception error [86]: "+e.getMessage());
        }  
        ps("DATABASE CREATO CORRETTAMENTE\nGIOCATORI REGISTRATI:\n"+UTENTI_REGISTRATI);
    }

    public static void ps(String s){
        System.out.println(s);
    }

}
