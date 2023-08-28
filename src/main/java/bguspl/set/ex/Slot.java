package bguspl.set.ex;
import java.util.List;
import java.util.ArrayList;
import bguspl.set.Env;

/**
 * Represents a single Slot in the game Table.
 * A Slot can contain a card and tokens placed by players.
 */
public class Slot {
    
    private Integer card; // Holds the card value for the Slot
    private List<Integer> playerToken; // Holds tokens placed by players
    private int slotId; // Unique identifier for the Slot
    private final Env env; // The game environment

    /**
     * Initializes a new Slot with the given slot ID and game environment.
     *
     * @param slotId The unique identifier for the Slot.
     * @param env The game environment.
     */
    public Slot(int slotId, Env env) {
        this.slotId = slotId;
        this.playerToken = new ArrayList<>();
        this.card = null;
        this.env = env;
    }

    /**
     * Places a card on this Slot.
     *
     * @param card The card to be placed.
     */
    public void placeCard(Integer card) {
        this.card = card;
    }

    /**
     * Places a token on this Slot for a given player.
     *
     * @param playerId The ID of the player placing the token.
     */
    public synchronized void placeToken(Integer playerId) {
        this.playerToken.add(playerId);
    }

    /**
     * Retrieves the card placed on this Slot.
     *
     * @return The card on the Slot.
     */
    public Integer getCard() {
        return card;
    }

    /**
     * Retrieves the list of player tokens placed on this Slot.
     *
     * @return A list of player IDs who have tokens on this Slot.
     */
    public synchronized List<Integer> getPlayerToken() {
        return this.playerToken;
    }

    /**
     * Removes the card from this Slot and clears all tokens.
     */
    public void removeCard() {
        this.card = null;
        removeAllTokens();
    }

    /**
     * Removes a player's token from this Slot.
     *
     * @param playerId The ID of the player whose token is to be removed.
     * @return True if the token was successfully removed; false otherwise.
     */
    public synchronized boolean removeToken(int playerId) {
        boolean placed = playerToken.contains(playerId);
        if (placed) {
            playerToken.remove(playerToken.indexOf(playerId));
        }
        return placed;
    }

    /**
     * Retrieves the unique ID of this Slot.
     *
     * @return The Slot ID.
     */
    public int getSlotId(){
        return slotId;
    }

    /**
     * Removes all tokens from this Slot.
     */
    public synchronized void removeAllTokens() {
        playerToken.clear();
    }
}
