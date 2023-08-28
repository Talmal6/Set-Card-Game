package bguspl.set.ex;
import bguspl.set.Env;
import java.beans.Transient;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)


    public Slot[] table;
    public volatile Queue<Integer> queue;
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */

     public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        table = new Slot[env.config.tableSize];
        for (int i = 0; i < table.length; i++) {
            table[i] = new Slot(i,env);
        }
        queue  = new LinkedList<>();
    }
    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
     public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        
    }

    public int getLength(){
        int size = table.length;
        return(size);
    }
    public int slotToCard(int slot){
        return this.slotToCard[slot] ;
    }
    public int cardToSlot(int card){
        return this.cardToSlot[card];
    }
    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */

    public int countCards() {
        int cards = 0;
        for (Slot slot : table)
            if (slot.getCard() != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */

    public void placeCard(int card, int slot) {
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        if(table[slot].getCard() == null){  
            table[slot].placeCard(card);
            env.ui.placeCard(card, slot);
        }
    
        
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */

    public void removeCard(int slot) {
        

        if (table[slot].getCard() != null){
            table[slot].removeCard();
            env.ui.removeCard(slot);
            env.ui.removeTokens(slot);
            while(!table[slot].getPlayerToken().isEmpty()){
                removeTokensFromSlot(slot);
            }
            }
        
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */

    public void placeToken(int player, int slot) {

        // Check if the player has already placed a token in the specified slot
        if (!Arrays.asList(table[slot].getPlayerToken()).contains(player) && table[slot].getCard() != null) {
            // Place the token and update the UI
            table[slot].placeToken(player);
            }
        
        
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */

    public boolean removeToken(int player, int slot) {

        boolean removed = table[slot].removeToken(player);
        if (removed == true){
            env.ui.removeToken(player, slot);
        }
        return removed;
        
    }
    /**
     * Removes all tokens belonging to a specific player from all slots on the table.
     *
     * @param player - the player whose tokens are to be removed
     */
    public void removeTokens(int player){
        for (Slot slot : table){
            if (slot.getPlayerToken().contains(player)){
                slot.removeToken(player);
            }
        }
    }

    /**
     * Removes all tokens from a specific slot on the table.
     *
     * @param slot - the slot from which all tokens are to be removed
     */
    public void removeTokensFromSlot(int slot){
        while(!table[slot].getPlayerToken().isEmpty()){
            for(int token : table[slot].getPlayerToken()){
                env.ui.removeToken(token, slot);
            }
            table[slot].removeAllTokens();
        }
    }

    /**
     * Retrieves all slots where a specific player has placed a token.
     *
     * @param player - the player whose token slots are to be retrieved
     * @return       - a list of slots where the player has a token
     */
    public List<Integer> getPlayerTokens(int player){
        List<Integer> tokens = new ArrayList<>();
        for (int i = 0; i < table.length; i++) {
            if (table[i].getPlayerToken().contains(player))
                tokens.add(i);
        }
        return tokens;
    }

    /**
     * Gets the Slot object at a specific index in the table.
     *
     * @param i - the index of the slot to retrieve
     * @return  - the Slot object at the specified index
     */
    public Slot getSlot(int i){
        return table[i];
    }

    /**
     * Retrieves all cards currently placed in slots on the table.
     *
     * @return - a list of cards currently on the table
     */
    public List<Integer> getTableCards() {
        List<Integer> cards = new ArrayList<>();
        for(Slot slot : table){
            if(slot.getCard() != null)
                cards.add(slot.getCard());
        }
        return cards;
    }

    /**
     * Inserts a player ID into the queue if it's their third click.
     * Ensures no duplicates.
     *
     * @param playerId - the player ID to insert
     */
    public synchronized void insertPlayerThirdClick(Integer playerId){
        boolean contains = false;
        if (!queue.isEmpty()) {
            for(int i : queue)
                if (i == playerId)
                    contains = true;
        }
        if (!contains) {
            queue.add(playerId);
        }
    }

    /**
     * Retrieves and removes the first player ID in the click queue.
     *
     * @return - the first player ID in the queue, or null if empty
     */
    public synchronized Integer pollFirstClick(){
        return queue.poll();
    }
} 


