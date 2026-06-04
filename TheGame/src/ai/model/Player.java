package ai.model;

public class Player {

    public static final int MAX_HEALTH = 100;
    public static final int MAX_AMMO   = 30;
    private static final double WORLD_MAX_X      = 800;
    private static final double WORLD_MAX_Y      = 600;
    private static final double BOUNDARY_MARGIN  = 20;

    private final int    id;
    private Position     position;
    private double       angle;
    private int          health;
    private int          score;
    private Weapon       currentWeapon;

    public Player(int id, double startX, double startY) {
        this.id            = id;
        this.position      = new Position(startX, startY);
        this.angle         = 0;
        this.health        = MAX_HEALTH;
        this.score         = 0;
        this.currentWeapon = new Weapon("Pistol", MAX_AMMO, MAX_AMMO, 50);
    }

    // ── Movement ─────────────────────────────────────────────────────────────

    public void move(double dx, double dy) {
        position.setX(position.getX() + dx);
        position.setY(position.getY() + dy);
    }

    public void rotateLeft(double radians) {
        angle -= radians;
        normalizeAngle();
    }

    public void rotateRight(double radians) {
        angle += radians;
        normalizeAngle();
    }

    public void moveForward(double distance) {
        clampToWorld(position.getX() + Math.cos(angle) * distance,
                     position.getY() + Math.sin(angle) * distance);
    }

    public void moveBackward(double distance) {
        clampToWorld(position.getX() - Math.cos(angle) * distance,
                     position.getY() - Math.sin(angle) * distance);
    }

    private void normalizeAngle() {
        while (angle < 0)            angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
    }

    private void clampToWorld(double newX, double newY) {
        position.setX(Math.max(BOUNDARY_MARGIN, Math.min(WORLD_MAX_X - BOUNDARY_MARGIN, newX)));
        position.setY(Math.max(BOUNDARY_MARGIN, Math.min(WORLD_MAX_Y - BOUNDARY_MARGIN, newY)));
    }

    // ── Weapon delegates ─────────────────────────────────────────────────────

    public boolean fire()        { return currentWeapon.decrementAmmo(); }
    public boolean reload()      { return currentWeapon.reload(); }
    public void    tickReload()  { currentWeapon.tickReload(); }
    public boolean isReloading() { return currentWeapon.isReloading(); }
    public int     getAmmo()     { return currentWeapon.getAmmo(); }

    // ── Health / score ───────────────────────────────────────────────────────

    public void takeDamage(int dmg) { health = Math.max(0, health - dmg); }
    public boolean isAlive()        { return health > 0; }
    public void addScore(int pts)   { score += pts; }

    public void pickupHealth(int amount) { health = Math.min(MAX_HEALTH, health + amount); }
    public void pickupAmmo(int amount)   { currentWeapon.addAmmo(amount); }

    // ── Getters / setters ────────────────────────────────────────────────────

    public int    getId()    { return id; }
    public double getX()     { return position.getX(); }
    public double getY()     { return position.getY(); }
    public double getAngle() { return angle; }
    public int    getHealth(){ return health; }
    public int    getScore() { return score; }

    public void setX(double x)         { position.setX(x); }
    public void setY(double y)         { position.setY(y); }
    public void setAngle(double angle) { this.angle = angle; normalizeAngle(); }
}
