package ai.model;

public class Enemy {

    public static final int    MAX_HEALTH      = 50;
    public static final int    DETECTION_RANGE = 200;
    public static final int    ATTACK_RANGE    = 100;
    public static final double MOVE_SPEED      = 3.0;

    private final int id;
    private Position  position;
    private int       health;
    private boolean   alive;

    public Enemy(int id, double x, double y) {
        this.id       = id;
        this.position = new Position(x, y);
        this.health   = MAX_HEALTH;
        this.alive    = true;
    }

    public boolean updateAI(double playerX, double playerY) {
        double dist = distanceTo(playerX, playerY);
        if (dist <= DETECTION_RANGE) {
            double dx  = playerX - position.getX();
            double dy  = playerY - position.getY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                position.setX(position.getX() + (dx / len) * MOVE_SPEED);
                position.setY(position.getY() + (dy / len) * MOVE_SPEED);
            }
        }
        return dist <= ATTACK_RANGE;
    }

    public void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
        if (health == 0) alive = false;
    }

    public double distanceTo(double px, double py) {
        double dx = position.getX() - px, dy = position.getY() - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int     getId()     { return id; }
    public double  getX()      { return position.getX(); }
    public double  getY()      { return position.getY(); }
    public int     getHealth() { return health; }
    public boolean isAlive()   { return alive; }
}
