import java.util.ArrayList;

//package ProjectLab;

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

    public Giocatore(String username, String password, ArrayList<String> partitegiocate, int p1, int p2, float p3, int p4, int p5, int p6){
        this.username=username;
        this.password=password;
        this.paroleGiocate=partitegiocate;
        this.partiteGiocate=p1;
        this.partiteVinte=p2;
        this.percentualeVinte=p3;
        this.streakVincite=p4;
        this.maxStreakVincite=p5;
        this.guessDistribution=p6;
    }
}
