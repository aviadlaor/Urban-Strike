package ai.model;

import java.util.ArrayList;
import java.util.List;

public class GameWorld {

    private Player      player;
    private List<Enemy> enemies = new ArrayList<>();
    private List<Item>  items   = new ArrayList<>();
    private int nextEnemyId = 0;

    public void initWorld(int screenWidth, int screenHeight) {
        player = new Player(0, screenWidth / 2.0, screenHeight / 2.0);
        spawnEnemy(200, 150);
        spawnEnemy(screenWidth - 200, screenHeight - 150);
        items.add(new Item(0, "Medkit",  new Position(150, 150)));
        items.add(new Item(1, "AmmoBox", new Position(620, 430)));
    }

    public Enemy spawnEnemy(double x, double y) {
        Enemy e = new Enemy(nextEnemyId++, x, y);
        enemies.add(e);
        return e;
    }

    public List<Integer> removeDeadEnemies() {
        List<Integer> removed = new ArrayList<>();
        enemies.removeIf(e -> {
            if (!e.isAlive()) {
                removed.add(e.getId());
                return true;
            }
            return false;
        });
        return removed;
    }

    public void removeCollectedItems() { items.removeIf(Item::isCollected); }

    public Player      getPlayer()  { return player; }
    public List<Enemy> getEnemies() { return enemies; }
    public List<Item>  getItems()   { return items; }
}
