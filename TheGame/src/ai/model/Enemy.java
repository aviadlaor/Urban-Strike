package ai.model;

public class Enemy {

    public static final int    MAX_HEALTH      = 150;
    public static final int    DETECTION_RANGE = 1500;
    public static final int    ATTACK_RANGE    = 120;
    public static final double MOVE_SPEED      = 3.0; // kept for reference

    private final int id;
    private Position  position;
    private int       health;
    private int       maxHealth;
    private double    moveSpeed;
    private boolean   alive;

    public Enemy(int id, double x, double y, int wave) {
        this.id        = id;
        this.position  = new Position(x, y);
        this.moveSpeed = Math.min(6.0, 2.5 + (wave - 1) * 0.8);
        this.maxHealth = (wave >= 3) ? 225 : MAX_HEALTH;
        this.health    = this.maxHealth;
        this.alive     = true;
    }

    public Enemy(int id, double x, double y) { this(id, x, y, 1); }

    public int updateAI(double playerX, double playerY) {
        double dist = distanceTo(playerX, playerY);
        if (dist <= DETECTION_RANGE) {
            double dx  = playerX - position.getX();
            double dy  = playerY - position.getY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                position.setX(position.getX() + (dx / len) * moveSpeed);
                position.setY(position.getY() + (dy / len) * moveSpeed);
            }
        }
        if (dist <= ATTACK_RANGE) return 1;
        if (dist <= 350) return 2;
        return 0;
    }

    public void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
        if (health == 0) alive = false;
    }

    public double distanceTo(double px, double py) {
        double dx = position.getX() - px, dy = position.getY() - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int     getId()        { return id; }
    public double  getX()         { return position.getX(); }
    public double  getY()         { return position.getY(); }
    public int     getHealth()    { return health; }
    public int     getMaxHealth() { return maxHealth; }
    public boolean isAlive()      { return alive; }
}
