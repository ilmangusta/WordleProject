package Src;

//package Src;

public class Main {
    /*
    Il progetto consiste nella implementazione di WORDLE, 
    un gioco di parole web-based, divenuto virale alla fine del 2021. 
    In questa sezione descriveremo le regole generali del gioco, in quella
    successiva le specifiche della versione di WORDLE da implementare.
    Il gioco consiste nel trovare una parola inglese formata da 5 lettere, 
    impiegando un numero massimo di 6 tentativi. WORDLE dispone di un vocabolario di 
    parole di 5 lettere, da cui estrae casualmente una parola SW (Secret Word), che gli 
    utenti devono indovinare. Ogni giorno viene selezionata una nuova SW, che rimane invariata 
    fino al giorno successivo e che viene proposta a tutti gli utenti che si collegano al 
    sistema durante quel giorno. Quindi esiste una sola parola per ogni giorno e tutti gli 
    utenti devono indovinarla, questo attribuisce al gioco un aspetto sociale. L’utente propone 
    una parola GW (Guessed Word) e il sistema inizialmente verifica se la parola è presente 
    nel vocabolario. In caso negativo avverte l’utente che deve immettere un’altra parola. 
    */

   //private static WordleClient wclient;

    public static void main(String[] args){
        new WordleClientMain();
        //new StartFrame(wclient);
    }
}


/*
 * 
 * sistemare count tentativi per ogni parola
 * 
 * . Il sistema memorizza, per ogni utente, le seguenti statistiche e le mostra a termine di ogni gioco:
   - numero partite giocate
   - percentuale di partite vinte
   - lunghezza dell’ultima sequenza continua (streak) di vincite
   -lunghezza della massima sequenza continua (streak) di vincite
   -guess distribution: la distribuzione di tentativi impiegati per arrivare alla soluzione del gioco, in ogni partita vinta dal giocatore
    
   sitemare selezione della secret word e ricerca della parola
 * sistemare streak vittorie e guess distrubtion 
 */
