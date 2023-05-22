default:
	javac -cp :gson-2.8.2.jar WordleServerMain.java 
	javac WordleClientMain.java 
	javac Main.java
	javac -cp :gson-2.8.2.jar Database.java


class:
	rm *.class

c:
	java WordleClientMain

s:
	java -cp :gson-2.8.2.jar WordleServerMain
