default:
	javac ./Src/Giocatore.java
	javac -cp :./Lib/gson-2.10.jar ./Src/Database.java
	javac -cp :./Lib/gson-2.10.jar ./src/WordleServerMain.java 
	javac ./Src/WordleClientMain.java 
	javac ./Src/ThreadMultiCast.java
	javac -cp :./Lib/gson-2.10.jar ./Src/Database.java


class:
	rm Src/*.class

c:
	java Src/WordleClientMain

s:
	java -cp :./Lib/gson-2.10.jar Src/WordleServerMain
