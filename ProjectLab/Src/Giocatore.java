package Src;

//package Src;
import java.util.ArrayList;

public class Giocatore {
    
    public String username;
    public String password;
    public ArrayList<String> paroleGiocate;
    public int partiteGiocate;
    public int partiteVinte;
    public float percentualeVinte;
    public int streakVincite;
    public int maxStreakVincite;
    public int guessDistribution;
    public int ultimoIdGiocato;

    public Giocatore(String username, String password, ArrayList<String> parolegiocate, int partitegiocate, int partitevinte, float percentualevinte, int streakvincite, int maxstreak, int guessdistribution, int ultimoidgiocato){
        this.username = username;
        this.password = password;
        this.paroleGiocate = parolegiocate;
        this.partiteGiocate = partitegiocate;
        this.partiteVinte = partitevinte;
        this.percentualeVinte = percentualevinte;
        this.streakVincite = streakvincite;
        this.maxStreakVincite = maxstreak;
        this.guessDistribution = guessdistribution;
        this.ultimoIdGiocato = ultimoidgiocato;
    }
}
