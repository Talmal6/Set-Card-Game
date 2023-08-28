package bguspl.set.ex;
import java.lang.Thread.State;
import bguspl.set.Env;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;


    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    public Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;
    /**
     * True iff game should be terminated due to an external event.
     */
    public volatile boolean terminate;
    public volatile boolean terminateAi;
    /**
     * The current score of the player.
     */
    private int score;

    private final Dealer dealer;
    public boolean gotPoint;
    public boolean gotPanelty;
    private boolean onFreeze;
    private volatile boolean terminatingStarted;
    public final Object AIlock;
    public final Object playerLock;
    public List<Integer> listActions;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        score = 0;
        onFreeze = false;
        AIlock = new Object();
        playerLock = new Object();
        listActions = new ArrayList<>(env.config.featureSize);
        
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            synchronized(listActions){
                try{
                    listActions.wait();
                }catch (InterruptedException e){}
            }
                playerActions();
                removeTokens();
                if(gotPanelty){penalty();}
                if(gotPoint){gotPoint();}
                if(terminateAi){terminateAI();}
                
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            /**
             *   Two options possible, first is doing 3 random key press, other one  is doing a right set, I can choose how right the AI 
             *   by changing the random in goodans to be more, often or not.   
             */
            while (!terminateAi) {
                int time = (int)(Math.random() * (3));
                int goodAnswer = (int)(Math.random() * (4));     
                 if(table.getPlayerTokens(this.id).size() == 0 && goodAnswer == 0){
                    List<Integer> set = table.getTableCards();
                    int[] goodSet = new int[0];
                    try{goodSet = (env.util.findSets(set, 1)).get(0);}catch(IndexOutOfBoundsException e){}
                    if(goodSet.length == env.config.featureSize){
                    for(int card : goodSet){
                           keyPressed(table.cardToSlot(card));
                           try {
                            aiThread.sleep(time*1000);
                        } catch(InterruptedException e){}
                        
                     }

                    }
                 }
            else{
                int slot = (int)(Math.random() * (table.getLength()));
                if(table.getSlot(slot) != null){
                    keyPressed(slot);
                }
                try {
                    aiThread.sleep(time*1000);
                } catch(InterruptedException e){}
            }
            if (onFreeze) {              
                synchronized(AIlock) {
                  try{
                if(!terminatingStarted)
                    AIlock.wait();
                  }catch (InterruptedException e){}
         }
        }
       }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminatingStarted = true;
        terminate = true;
    }


    //if theres AI thread terminates AI first to keep termination order
    public void terminateAI(){
         terminateAi = true;

    }
    public void playerActions(){
        for(int i : listActions){
            table.placeToken(this.id, i);
        }
        dealer.wakeUp();
        synchronized(playerLock){
            try{
                playerLock.wait();
            }catch(InterruptedException e){}}
        synchronized(listActions){
            listActions.clear();
        }
    }
    public void removeToken(int slot){
        synchronized(listActions){
        if(listActions.contains(slot)){
            listActions.remove(listActions.indexOf(slot));
        }
        }
        env.ui.removeToken(id, slot);
    }

    public  void removeTokens(){
        for(Slot slot : table.table){
            slot.removeToken(id);
            env.ui.removeToken(id, slot.getSlotId());
        }
    }
    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(listActions){
        if(listActions.size() <env.config.featureSize && !onFreeze && dealer.isCounting() && table.getSlot(slot).getCard() != null)
            
            if(!listActions.contains(slot)){
                listActions.add(slot);
                env.ui.placeToken(id, slot);
            }
            else{
                listActions.remove(listActions.indexOf(slot));
                env.ui.removeToken(id, slot);
                
            }           

        if (listActions.size() == env.config.featureSize) {
            table.insertPlayerThirdClick(id);
            {listActions.notifyAll();}
        }
        }
    }
    //give the player a panelty
    public void givePanelty(){
        this.gotPanelty = true;
        
    }
    //give the player a point
    public void givePoint(){
        this.gotPoint = true;
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void gotPoint() {
        if(!terminatingStarted) // fixing termination bug
            env.ui.setScore(id, ++score);
        freezePlayer(env.config.pointFreezeMillis);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        gotPoint = false;
        
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if(!terminatingStarted) // fixing termination bug
            freezePlayer(env.config.penaltyFreezeMillis);
        gotPanelty = false;
    }
    //freezing player for the time set in the configuration
    public void freezePlayer(long time){
        boolean stop = false;
        onFreeze = true;
        try{
            while(!stop&&!terminate){
             synchronized(playerLock){   
                env.ui.setFreeze(id, time);
             }
                playerThread.sleep(1000);
                time = time - 1000;
                if(time <= 0 ){
                    env.ui.setFreeze(id, 0);
                    stop = true;
                    break;
                }
            }
         } catch (InterruptedException e) {}
         onFreeze = false;
       synchronized(AIlock) {
         if(!human){
            AIlock.notifyAll();
         }
       }
    }
    //Get Players Score
    public int getScore() {
        return score;
    }

    // return if the player is human
    public boolean isHuman(){
        return(human);
    }
    
    public boolean onFreezed(){
        return(this.onFreeze);
    }
}
