package bguspl.set.ex;
import bguspl.set.Env;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private long time;
    Thread[] playersThread;
    Thread[] aiThreads;
    private boolean wakeup;
    private boolean reset;
    private boolean stopTimerloop;
    private boolean cardPlaced;
    private volatile boolean terminate;
    private boolean stopCounting;
    private boolean startingCards;
    private volatile boolean terminatingStarted;
    int timeFactor;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersThread = new Thread[players.length];
        wakeup = false;
        stopCounting = true;
        stopTimerloop = false;
        startingCards = false;
        timeFactor = 10;
        
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        startingCards = true;
        placeCardsOnTable();
        startingCards = false;
        searchForAiThreads();
        startPlayerThreads();
        while (!shouldFinish() && !terminate) {
            placeCardsOnTable();
            timerLoop();
            removeAllCardsFromTable();
            
        }
        if(shouldFinish())
            announceWinners();

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }
    

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        this.stopTimerloop  = false;
        time = System.currentTimeMillis();
        while (!stopTimerloop) {
            if(env.config.hints)
                table.hints();
            wakeUpPlayers();
            updateTimerDisplay(reset);
            removeCardsFromTable();
            placeCardsOnTable();
            checkGameOver();
            if(terminate){
                this.stopTimerloop = true;
            }
            if(env.config.turnTimeoutMillis > 0 && reshuffleTime-System.currentTimeMillis()<0)
                this.stopTimerloop = true;
            if(env.config.turnTimeoutMillis <= 0 && checkTableCards()){
                this.stopTimerloop = true;
            }
            
            
        }
       
    
    }
    private void checkGameOver(){
        ArrayList list = new ArrayList<>();
        for(Slot slot:table.table){
            if(slot.getCard()!=(null))
                list.add(slot.getCard());
        }
        if(env.util.findSets(deck, 1).size() == 0 && env.util.findSets(list, 1).size() == 0){
            terminate = true;
        }
    }
    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for(Player player : players){
            if(!player.isHuman()){
              player.terminateAI();
              while(player.aiThread.isAlive()){if(player.aiThread.getState() == State.TIMED_WAITING)player.aiThread.interrupt(); synchronized(player.AIlock){player.AIlock.notifyAll();}}
               try { player.aiThread.join(); } catch (InterruptedException ignored) {}
            }
        }
        for(Player player : players){
            player.terminate();
            while(player.playerThread.isAlive()){if(player.playerThread.getState() == State.TIMED_WAITING)player.playerThread.interrupt(); synchronized(player.playerLock){player.playerLock.notifyAll();};synchronized(player.listActions){player.listActions.notifyAll();}}
            try { player.playerThread.join(); } catch (InterruptedException ignored) {}
        }

        terminate = true;
        stopTimerloop=true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate && (env.util.findSets(deck, 1).size() == 0);
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
     private void removeCardsFromTable() {
            while(!table.queue.isEmpty()){
            
            Integer player = table.pollFirstClick();
            List<Integer> tokens = table.getPlayerTokens(player);
            if (tokens.size() == env.config.featureSize) {
                int[] tokensArray = tokens.stream().mapToInt(index -> table.slotToCard(index)).toArray();
                if (env.util.testSet(tokensArray)) {
                    for (int j = 0; j < table.getLength(); j++) {
                        if (table.getSlot(j).getPlayerToken().contains(player)) {
                            if(startingCards){
                                delay();
                                table.removeCard(j);
                            }
                            else{    
                                removeSingleCard(j);
                            }
                        }
                    }
                    players[player].gotPoint = true;
                } 
                else {
                    players[player].gotPanelty = true;
                }
            }
        }
            
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    public void placeCardsOnTable() {
        Collections.shuffle(deck);
        int i =0;
        while(!deck.isEmpty() && table.countCards() < table.getLength()){
          if(table.getSlot(i).getCard() == null){
            int card = deck.get(0);
            deck.remove(0);
            if(startingCards){
                delay();
                table.placeCard(card, i);
            }
            else{      
                placeSingleCard(card,i);
            }
            reset = true;
            }
            i++;
        }

    }
    public void delay(){
        try{
            Thread.currentThread().sleep(env.config.tableDelayMillis);
        } catch (InterruptedException e) {}
    }

    //used to suppurt env cofiguration (tableDelayMillis), while delaying also moving clock
    public void placeSingleCard(int card,int slot){
        
        cardPlaced = true;
        timeFactor =10;
        for(int i = 0 ; i<env.config.tableDelayMillis/10 ; i++){
            updateTimerDisplay(false);
        }
        if(!(env.config.turnTimeoutMillis > 0))
            timeFactor = 10;
        cardPlaced = false;
        
        table.placeCard(card,slot);
    }
    public void removeSingleCard(int slot){
        for(Player player: players){
            player.removeToken(slot);
        }
        cardPlaced = true;
        timeFactor =10;
        for(int i = 0 ; i<env.config.tableDelayMillis/10 ; i++){
            updateTimerDisplay(false);
        }
        if(!(env.config.turnTimeoutMillis > 0))
        timeFactor = 1000;
        cardPlaced = false;
        table.removeCard(slot);
    }
    


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        stopCounting = false;
        boolean warn  =false;
        if (reset == true){
            reshuffleTime = System.currentTimeMillis() +env.config.turnTimeoutMillis+999;
        }
        while(!stopCounting&&!terminate){
        if(env.config.turnTimeoutMillis > 0){
            if(reshuffleTime-System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis){
                 warn = true;
                timeFactor = 10;
            }
            if(!terminatingStarted) 
                env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), warn);
            if (reshuffleTime-System.currentTimeMillis() < 0.01){
                stopCounting = true;
            }
        }
        if(!warn && !cardPlaced){timeFactor = 10;};
        if (env.config.turnTimeoutMillis == 0){
            if(!terminatingStarted)
                env.ui.setElapsed(System.currentTimeMillis()-time);
        }
           try{
                Thread.currentThread().sleep(timeFactor);
            } catch (InterruptedException e) {}


        if ((env.config.turnTimeoutMillis <= 0 && checkTableCards())||wakeup||cardPlaced){
            stopCounting = true;
        }
        
        }
        this.wakeup = false;
        this.reset = false;
    }

    /**
     * Returns all the cards from the table to the deck.
     */

    private void removeAllCardsFromTable() { 
        for (int j =0 ; j< table.getLength(); j++){
            for (int i = 0; i < players.length; i++) {
                if (table.getSlot(j).getPlayerToken().contains(players[i].id)){
                    table.removeToken(players[i].id, j);
                }
            }
            if(table.getSlot(j).getCard()!=null)
                deck.add(table.getSlot(j).getCard());
            removeSingleCard(j);
        }
            

        if(env.config.turnTimeoutMillis>0 && reshuffleTime<0)
            reshuffleTime = System.currentTimeMillis() +env.config.turnTimeoutMillis+999;
        

        
    }

    /**
     * Check who is/are the winner/s and displays them.
     */

    private void announceWinners() {
        int maxScore = 0;
        List<Integer> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getScore() > maxScore) {
                maxScore = player.getScore();
                winners.clear();
                winners.add(player.id);
            } else if (player.getScore() == maxScore) {
                winners.add(player.id);
            }
        }
        int[] winnerIds = new int[winners.size()];
        for (int i = 0; i < winners.size(); i++) {
            winnerIds[i] = winners.get(i);
        }
        env.ui.announceWinner(winnerIds);
        try{
            Thread.currentThread().sleep(env.config.endGamePauseMillies);
        }catch(InterruptedException e){}
        terminate();
    }
        
    

    //notify if player in waiting
    public void wakeUpPlayers(){
        for (Player player : players){
            if(!player.onFreezed())
                synchronized(player.playerLock){player.playerLock.notifyAll();}
        }
    }
    
   
    //Stating player threads
    public void startPlayerThreads(){ 
        for (int i = 0 ; i < players.length;  i++){
            playersThread[i] = new Thread(players[i],"player");
            playersThread[i].start();
        }
        //Ugly Solution
        try{
            Thread.currentThread().sleep(100);
        }catch (InterruptedException e){}
        for(Player player: players){
            if(!player.isHuman()){
                player.aiThread.start();
            }
        }

    }
    // used to wake up the dealer on timer
    public void wakeUp(){
        this.wakeup = true;

    } 
    //looking for a legal set on table
    public boolean checkTableCards(){
        if(env.util.findSets(table.getTableCards(),1).size() >0){
                return false;
            }       
        return true;
    }    


    //looking for players who are not human
    public void searchForAiThreads(){
        int count = 0;
        for(Player player : players){
            if(!player.isHuman())
                ++count;
        }
        aiThreads = new Thread[count];
    }
    //true if dealer is
    public boolean isCounting(){
        return(!stopCounting);
    }
}
