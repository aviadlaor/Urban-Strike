package ai.model;

public class Item {
    private final int itemId;
    private final String itemType;   // "Medkit" | "AmmoBox"
    private final Position position;
    private boolean collected = false;

    public Item(int itemId, String itemType, Position position) {
        this.itemId   = itemId;
        this.itemType = itemType;
        this.position = position;
    }

    public int getId()              { return itemId; }
    public String getItemType()     { return itemType; }
    public Position getPosition()   { return position; }
    public boolean isCollected()    { return collected; }
    public void markAsCollected()   { collected = true; }
}
