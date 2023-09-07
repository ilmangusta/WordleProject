
# LABORATORIO DI RETI A.A. 2022/23
### Progetto Laboratorio di reti: Wordle

## INDICE:

#### 1. INTRODUZIONE AL GIOCO

#### 2. SCHEMA GENERALE CLASSI
```
2.1 PROCESSI
```

#### 3. STRUTTURE DATI UTILIZZATE

#### 4. COMPILAZIONE ESECUZIONE E MANUALE D'USO
```
4.1 COMPILAZIONE ED ESECUZIONE
4.2 MANUALE D'USO
```

## 1. INTRODUZIONE:

Il progetto consiste nell'implementazione del gioco Wordle, un gioco web-based che consente ai giocatori di
provare ad indovinare una parola giornaliera di 5 lettere in 6 tentativi utili ricevendo feedback sui tentativi
effettuati, ma con alcune modifiche che rendono l'esperienza di gioco ancora più longeva. Il gioco offre
inoltre un aspetto social in quanto permette di condividere i propri suggerimenti dell'ultimo round giocato
su una piattaforma e di riceverli su richiesta in qualsiasi momento da altri giocatori connessi. Tra le
modifiche troviamo: 12 tentativi utili anziché i classici 6, parole di lunghezza 10 lettere e non 5, il timer di
selezione della parola non giornaliero come nella versione originale ma a libera scelta del programmatore.
OSSERVAZIONI:
La mia implementazioni offre delle funzionalità in più non richieste col tracciamento e mantenimento
delle parole giocate da parte dei giocatori e quindi alcune strutture dati superflue rispetto a quanto
richiesto dal progetto.
La mia implementazione del progetto permette con un terminale di effettuare nel tempo più accessi a
più account senza dover eseguire necessariamente più volte il processo WordleClientMain.java. Infatti
eseguendo l'operazione di logout nella schermata principale del gioco, il server non chiude la
connessione col client ma semplicemente il giocatore viene disconnesso e solo successivamente ho
l'occasione di eseguire nuovamente l'operazione di registrazione e/o logout.
Con la mia implementazione le statistiche di gioco nel json vengono aggiornate solo quando il server
viene spento o per malfunzionamento. Nel mio caso viene simulato quando riceve un segnale di
terminazione tramite il comando ctrl + c. Avevo anche implementato altre soluzioni simili che
aggiornavano il json in diverse situazioni: subito dopo la disconnessione da parte di un client.
Ho consegnato il progetto con un file Makefile che semplifica la compilazione. Vedi dettagli nella
sezione 5.

## 2. SCHEMA GENERALE CLASSI:

Viene qui spiegato il funzionamento dei thread e processi principali, dove e quando vengono attivati.

### 2.1 PROCESSI:


#### LATO SERVER:
```
processo WordleServerMain. Si occupa di inizializzare il server e mettersi in attesa di accettare
nuove connessioni socket con i client. Parte subito con l’esecuzione del comando.
troviamo qui un CachedThreadPool di processi ThreadServer di default. Utilizzare un cached
pool in una situazione come questa si rivela essere la scelta migliore e più efficiente. Una
situazione in cui abbiamo un task (client) esattamente per ogni thread che si crea.
sempre nel WordleServerMain faccio partire un timer per la selezione della parola nuova che
altro non è che un thread che aspetta di essere schedulato secondo l'intervallo di tempo che il
programmatore sceglie definito nella variabile PERIOD, questa viene definita in Secondi nel
file config. Terminato il timer il task viene eseguito chiamando il metodo run().
ancora nelWordleServerMain ho aggiunto uno shotdownhook, altro non è che creare un nuovo
ThreadTermination che permette al server di ricevere il segnale ctrl + c ed effettuare una
chiusura controllata del server, terminando il pool di thread e aggiornando il file json. Ho
deciso di scriverlo cosi ma potevo creare un altra classe thread e passarla dento come
parametro al addShutDownHook( classe thread).
processo ThreadServer. Viene creato quando il server accetta una nuova connessione e dopodiché
parte. Questo si occupa di gestire ed elaborare le richieste del client
```

#### LATO CLIENT:
```
processo WordleClientMain. Si occupa di impostare la connessione iniziale col server e dopodiché
di gestire l'interazione principale con l'utente.
processo ThreadMultiCast. Ogni volta che un utente accede con successo all'account il client crea
e fa partire un ThreadMultiCast per il gruppo sociale. Questo ha il compito di ricevere in maniera
passiva i messaggi dal server e aggiungerli nella lista.
anche qui ho implementato un ThreadTermination per ricevere il segnale ctrl + c ed effettuare una
disconnessione controllata del giocatore, mandare i dovuti messaggi al server e chiudere
```

## 3. STRUTTURE DATI UTILIZZATE:

Vengono qui spiegate le motivazioni dell'uso delle principali strutture dati. Ho deciso di utilizzare diverse
strutture per mantenere le informazioni, tra cui:
1. GAME_ID: utilizzo un AtomicInteger per assicurarmi che la lettura e scrittura sia atomica e non
avvengano episodi di concorrenza;
2. SECRET_WORDS: utilizzo una lista di elementi di tipo string, sincronizzata grazie alla libreria java
“Collections.synchronized(new Object)”. Viene utilizzata per salvare tutte le parole estratte durante una
sessione in cui il server è online e viene utilizzata per recuperare e mantenere la SECRET_WORD a
cui sta giocando il giocatore, se vengono estratte altre nuove nel mentre. Viene creata nel server main e
passata ai ThreadServer.
3. STATS_GIOCATORI: uso una lista sincronizzata come SECRET_WORDS di elementi di tipo
Giocatore per mantenere tutte le informazioni sulle statistiche. Ad ogni round terminato questa viene
aggiornata da ogni ThreadServer per questo è necessario utilizzare una struttura sincronizzata. E’
utilizzata per aggiornare le informazioni nel json. Viene creata nella classe Database e successivamente
raccolte le informazioni nel server main.
4. UTENTI_REGISTRATI: ho utilizzato una ConcurrentHashMap che garantisce la mutua esclusione tra
thread. Una mappa <username, password> che mantiene le informazioni dei giocatori registrati per
rendere più immediato il controllo durante la fase di registrazione. Viene creata nella classe Database e
successivamente raccolte le informazioni nel WordleServerMain.
5. UTENTI_CONNESSI: come le precedenti si tratta di una lista sincronizzata di elementi di tipo string
che utilizzo per gestire la fase di accesso e mantenere consistente gli utenti che accedono e che si
disconnettono. Caso di più utenti che vogliono accedere su più terminali contemporaneamente. Viene
creata nel server main e passata aiThreadServer.
6. LIST_MESSAGE: anche questa una lista sincronizzata (eventualmente se dovessero capitare situazioni
in cui il thread multicast sta aggiungendo in coda e il client sta leggendo dalla lista) di elementi di tipo
string che viene creata nel processo WordleClientMain e viene passata come parametro al
ThreadMultiCast che si occupa di ricevere i messaggi dal server e salvarli proprio in questa struttura
dati. Grazie a ciò ogni puó recuperare i messaggi da questa struttura dati e non fare richiesta al server.

## 4. COMPILAZIONE ESECUZIONE E MANUALE D'USO:

Il progetto è suddiviso in cartelle.
1. Config: contiene i due file Properties che vengono utilizzati per la configurazione del client e server.
2. DB: contiene il file json che simula il comportamento di un database in quanto crea le informazioni che
vengono salvate nelle strutture dati.
3. Jar: contiene i due file Jar per client e server come richiesto dalle specifiche del progetto. Permette
anche un metodo alternativo di esecuzione del progetto.
4. Src: cartella destinata ai file sorgenti ovvero i file .java e post compilazione .class che compongono il
progetto
5. Text: cartella destinata al file testuale contenente tutte le parole che possono essere estratte.
Sempre nel progetto è presente un file Makefile che ho creato per semplificare la sintassi per la
compilazione ed esecuzione dei file .java e .class.

### 4. 1 Compilazione ed esecuzione (MacOS):

Di seguito viene riportato come eseguire con i comandi la compilazione ed esecuzione. Tutte le operazioni e
comandi devono essere eseguiti nella cartella del progetto WORDLE_PROJECT.
COMPILAZIONE:
```
javac ./Src/WordleClientMain.java_ (LATO CLIENT)
javac -cp :./Lib/gson-2.10.jar ./src/WordleServerMain.java_ (LATO SERVER)
```

ESECUZIONE:
```
java Src/WordleClientMain (LATO CLIENT)
java -cp :./Lib/gson-2.10.jar Src/WordleServerMain (LATO SERVER)
```

ESECUZIONE ALTERNATIVA CON .JAR:

```
java -jar ./Jar/WORDLE_CLIENT.jar (LATO CLIENT)
java -jar ./Jar/WORDLE_SERVER.jar (LATO SERVER)
```

Sempre dentro la cartella WORDLE_PROJECT si ha la possibilità di eseguire dei comandi precisi
alternativi ai classici grazie all'utilizzo del Makefile:
COMANDI CON MAKEFILE:
```
make_ : esegue i due comandi per la compilazione del client e server;
make class_ : rimuove tutti gli eseguibili .class nella cartella Src del progetto;
make c_ : comando per eseguire il client;
make s_ : comando per eseguire il server;
make c-jar_ : comando per eseguire client con file .jar;
make s-jar_ : comando per eseguire server con file .jar;
```


### 4.2 MANUALE D'USO:

Vengono qui spiegati i comandi principali, come utilizzarli durante la sessione e soprattutto il modo in cui
mostro le notifiche dei giocatori.
All'avvio del processo client con successo viene chiesto fin da subito una scelta:
```
1. [REGISTRAZIONE]
2. [ACCESSO]
3. [CHIUDI TERMINALE]
```

Nel progetto viene consegnato un file .json simulando un database contenente le informazioni dei giocatori.
Questo file contiene già 2 giocatori che possono essere utilizzati per il testing. In alternativa è possibile
creare altri nuovi utenti.
Tutte possono essere selezionate sia inserendo il comando (maiuscolo o minuscolo) sia inserendo il numero
del comando.
Sia Registrazione che Accesso richiedono Nome utente e Password in due scansioni differenti. Chiudi
terminale termina la sessione e chiude la console.
Una volta eseguito l'accesso ci vengono stampati le possibili scelte da fare:

```
1. [GIOCA]: Inizia un nuovo round di wordle;
2. [STATISTICHE]: Mostra le statistiche di gioco dell'utente;
3. [CONDIVIDI]: Condividi i risultati del gioco sul social;
4. [NOTIFICHE]: Mostra notifiche di gioco inviate dai giocatori;
5. [ESCI]: Termina la sessione di gioco
6. [HELP PER LEGENDA].
```
Anche qui tutte le azioni possono essere selezionate sia inserendo il comando (maiuscolo o minuscolo) sia
inserendo il numero del comando.
Quando la richiesta di gioco va a buon fine, ho due possibilità di azioni:
```
1. [GUESSED WORD]: Inserisci la tua guessed word;
2. [ESCI]: Termina la sessione di gioco.
```
Quando inserisco una parola i colori indicano lo stato della lettera:
Presente nella SECRET_WORD e in posizione corretta (colore verde - “[V]”) (Ho cambiato dalle
specifiche che richiedeva “[+]”)
Presente nella SECRET_WORD ma in posizione errata (colore giallo - “[?]”)
Assente nella SECRET_WORD (nessun colore/grigio - “[X]”)
Il suggerimento mi viene mostrato in due modi per facilitare la visibilità degli errori.
Il secondo modo di visualizzare il messaggio è quello che compone il suggerimento che sarà inviato al
server e visto dagli altri utenti.

Per smettere di giocare se l'utente sta cercando di indovinare la parola può selezionare l'opzione “esci” e
successivamente verrà portato alla schermata principale. Dopodiché l'utente può proseguire chiedendo di
uscire sempre col comando “esci”. A questo punto un altro utente può accedere o registrare, o se si vuole
terminare l'esecuzione del programma si digita il comando “chiudi terminale”.
