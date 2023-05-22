public class ThreadScegliParola implements Runnable{


    public ThreadScegliParola(){



    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        while(true){
            //aspetta il timer e modifica password

            /* 
            int randomTime=(int)((Math.random()*2-1)+1);
            Timer T = new Timer();
            TimerTask selectWord = new TimerTask(){
                @Override
                public void run(){
                    System.out.println("Orario nuova parola arrivato!");
                    stop=true;
                }
            };
            
            T.schedule(selectWord, randomTime*1000);

            try {
                while (true) != null){
                    Thread.sleep(50);
                    //ps(SecretWord);
                    if(stop){
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            */
        }
        
    }
    
}
