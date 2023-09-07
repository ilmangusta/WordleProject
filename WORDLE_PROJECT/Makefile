default:
	- javac -cp :./Lib/gson-2.10.jar ./src/WordleServerMain.java 2> /dev/null
	- javac ./Src/WordleClientMain.java 2> /dev/null

class:
	- rm Src/*.class 2> /dev/null

c:
	java Src/WordleClientMain 

s:
	java -cp :./Lib/gson-2.10.jar Src/WordleServerMain 

c-jar:
	java -jar ./Jar/WORDLE_CLIENT.jar

s-jar:
	java -jar ./Jar/WORDLE_SERVER.jar

