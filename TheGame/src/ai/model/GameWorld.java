package ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameWorld {

    private Player      player;
    private List<Enemy> enemies = new ArrayList<>();
    private List<Item>  items   = new ArrayList<>();
    private List<CityElement> cityElements = new ArrayList<>();
    private int nextEnemyId = 0;

    private WaveManager waveManager = new WaveManager();
    private GameStats   stats       = new GameStats();
    private double screenW, screenH;

    public WaveManager        getWaveManager()   { return waveManager; }
    public List<CityElement>  getCityElements()  { return cityElements; }
    public GameStats           getStats()         { return stats; }

    public void initWorld(int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        player = new Player(0, screenWidth / 2.0, screenHeight / 2.0);
        items.add(new Item(0, "Medkit",  new Position(150, 150)));
        items.add(new Item(1, "AmmoBox", new Position(620, 430)));
        spawnCityElements();
        stats.reset();
    }

    private void spawnCityElements() {
        Random rng = new Random(99);
        CityElement.Type[] types = CityElement.Type.values();
        int count = 28;
        double margin = 80;
        for (int i = 0; i < count; i++) {
            CityElement.Type type = types[rng.nextInt(types.length)];
            double x = margin + rng.nextDouble() * (screenW - margin * 2);
            double y = margin + rng.nextDouble() * (screenH - margin * 2);
            if (Math.abs(x - screenW/2) < 120 && Math.abs(y - screenH/2) < 120) continue;
            cityElements.add(new CityElement(type, x, y));
        }
    }

    public Enemy spawnEnemy(double x, double y, int wave) {
        Enemy e = new Enemy(nextEnemyId++, x, y, wave);
        enemies.add(e);
        return e;
    }

    public Enemy spawnEnemy(double x, double y) { return spawnEnemy(x, y, 1); }

    public List<Enemy> spawnWave(int wave) {
        List<Enemy> spawned = new ArrayList<>();
        int count = waveManager.enemiesForWave(wave);
        double[][] spawnPoints = {
            {60, 60}, {screenW - 60, 60},
            {60, screenH - 60}, {screenW - 60, screenH - 60},
            {screenW / 2.0, 60}, {60, screenH / 2.0}
        };
        for (int i = 0; i < count; i++) {
            double[] pt = spawnPoints[i % spawnPoints.length];
            spawned.add(spawnEnemy(pt[0], pt[1], wave));
        }
        return spawned;
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

    public Item tryDropItem(double x, double y) {
        if (Math.random() > 0.4) return null;
        int id = nextEnemyId + 1000;
        String type = Math.random() < 0.55 ? "Medkit" : "AmmoBox";
        Item item = new Item(id, type, new Position(x, y));
        items.add(item);
        return item;
    }

    public void removeCollectedItems() { items.removeIf(Item::isCollected); }

    public boolean isPlayerInCover(double px, double py) {
        for (CityElement el : cityElements) {
            double dx   = px - el.getX();
            double dy   = py - el.getY();
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist <= el.getRadius() + 30) return true;
        }
        return false;
    }

    public boolean hasLineOfSight(double fromX, double fromY,
                                   double toX,   double toY) {
        for (CityElement el : cityElements) {
            double dx  = toX - fromX;
            double dy  = toY - fromY;
            double len = Math.sqrt(dx*dx + dy*dy);
            if (len < 1) return true;
            double t = ((el.getX()-fromX)*dx + (el.getY()-fromY)*dy) / (len*len);
            t = Math.max(0, Math.min(1, t));
            double closestX = fromX + t*dx;
            double closestY = fromY + t*dy;
            double distToLine = Math.hypot(el.getX()-closestX, el.getY()-closestY);
            if (distToLine < el.getRadius() * 0.85) return false;
        }
        return true;
    }

    public Player      getPlayer()  { return player; }
    public List<Enemy> getEnemies() { return enemies; }
    public List<Item>  getItems()   { return items; }
}

