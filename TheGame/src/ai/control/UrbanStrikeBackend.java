package ai.control;

import ai.model.Enemy;
import ai.model.GameWorld;
import ai.model.Item;
import ai.model.Player;
import shared.ui_ports.TeamUiPort;

import java.util.ArrayList;
import java.util.List;

public class UrbanStrikeBackend {

    public static final int SCREEN_W = 800;
    public static final int SCREEN_H = 600;
    public static final double MOVE_STEP = 15.0;
    public static final double ROTATE_STEP = Math.PI / 18; // 10 degrees per keypress
    public static final int BULLET_DAMAGE = 50;
    public static final int ENEMY_DAMAGE = 10;
    public static final int SCORE_PER_KILL = 100;
    private static final int FIRE_RANGE = 400;

    private GameWorld world = new GameWorld();
    private List<Integer> lastRemovedEnemies = new ArrayList<>();

    // ────────────────────────────────────────────
    // State Access for UI/Router
    // ────────────────────────────────────────────

    public GameWorld getWorld() { return world; }
    public List<Integer> getLastRemovedEnemies() { return lastRemovedEnemies; }

    // ────────────────────────────────────────────
    // GameState – clean snapshot for UI pull access
    // ────────────────────────────────────────────

    public static class GameState {
        public final Player player;
        public final List<Enemy> enemies; // live references; snapshot before iterating

        GameState(Player player, List<Enemy> enemies) {
            this.player = player;
            this.enemies = enemies;
        }
    }

    public GameState getState() {
        return new GameState(world.getPlayer(), world.getEnemies());
    }

    // ────────────────────────────────────────────
    // Initialization
    // ────────────────────────────────────────────

    public void startGame() {
        world.initWorld(SCREEN_W, SCREEN_H);

        Player player = world.getPlayer();
        TeamUiPort uiPort = TeamUiPort.getInstance();

        if (uiPort instanceof ai.ui.TeamUiPortImpl) {
            ((ai.ui.TeamUiPortImpl) uiPort).setGameWorld(player, world.getEnemies());
        }

        uiPort.showPlayer(player.getX(), player.getY());
        uiPort.updateHealth(player.getHealth());
        uiPort.updateScore(player.getScore());
        uiPort.updateAmmo(player.getAmmo());

        for (Enemy enemy : world.getEnemies()) {
            uiPort.addEnemy(enemy.getId(), enemy.getX(), enemy.getY());
        }

        for (Item item : world.getItems()) {
            uiPort.addItem(item.getId(), item.getPosition().getX(),
                           item.getPosition().getY(), item.getItemType());
        }

        uiPort.log("Urban Strike started! Player at center, 2 enemies spawned.");
    }

    // ────────────────────────────────────────────
    // UC2: Move Player
    // ────────────────────────────────────────────

    public void movePlayer(String direction) {
        Player p = world.getPlayer();

        switch (direction) {
            case "UP":    p.moveForward(MOVE_STEP);    break;
            case "DOWN":  p.moveBackward(MOVE_STEP);   break;
            case "LEFT":  p.rotateLeft(ROTATE_STEP);   break;
            case "RIGHT": p.rotateRight(ROTATE_STEP);  break;
        }

        TeamUiPort.getInstance().updatePlayer(p.getX(), p.getY());
    }

    // ────────────────────────────────────────────
    // UC1: Fire Weapon
    // ────────────────────────────────────────────

    public void fireWeapon() {
        Player p = world.getPlayer();

        if (!p.fire()) {
            TeamUiPort.getInstance().log("Fire failed: no ammo");
            return;
        }

        TeamUiPort.getInstance().updateAmmo(p.getAmmo());
        TeamUiPort.getInstance().showFireEffect();

        Enemy target = findClosestEnemyInSight(p.getX(), p.getY(), p.getAngle(), FIRE_RANGE);
        if (target != null) {
            target.takeDamage(BULLET_DAMAGE);
            TeamUiPort.getInstance().log("Hit enemy " + target.getId() + " | HP left: " + target.getHealth());
            TeamUiPort.getInstance().updateEnemy(target.getId(), target.getX(), target.getY());

            if (!target.isAlive()) {
                p.addScore(SCORE_PER_KILL);
                TeamUiPort.getInstance().updateScore(p.getScore());
                TeamUiPort.getInstance().removeEnemy(target.getId());
                TeamUiPort.getInstance().log("Enemy " + target.getId() + " killed! Score: " + p.getScore());
            }
        }
    }

    // ────────────────────────────────────────────
    // UC: Pick Up Item
    // ────────────────────────────────────────────

    public void processPickUpItem() {
        Player p = world.getPlayer();
        if (p == null) return;
        TeamUiPort uiPort = TeamUiPort.getInstance();
        for (Item item : world.getItems()) {
            if (item.isCollected()) continue;
            if (item.getPosition().distanceTo(p.getX(), p.getY()) <= 60) {
                item.markAsCollected();
                if ("Medkit".equals(item.getItemType())) {
                    p.pickupHealth(30);
                    uiPort.updateHealth(p.getHealth());
                } else {
                    p.pickupAmmo(15);
                    uiPort.updateAmmo(p.getAmmo());
                }
                uiPort.removeItem(item.getId());
                uiPort.showPickupMessage("Picked up " + item.getItemType() + "!");
                uiPort.log("Picked up " + item.getItemType());
            }
        }
        world.removeCollectedItems();
    }

    // ────────────────────────────────────────────
    // UC: Reload Weapon
    // ────────────────────────────────────────────

    public void reloadWeapon() {
        Player p = world.getPlayer();
        if (p == null) return;
        if (p.reload()) {
            TeamUiPort.getInstance().log("Reloading...");
            TeamUiPort.getInstance().updateAmmo(p.getAmmo());
        }
    }

    private static final double FIRE_FOV = Math.PI / 3; // matches Raycaster FOV

    private Enemy findClosestEnemyInSight(double px, double py, double angle, double maxRange) {
        Enemy closest = null;
        double minDist = maxRange;
        for (Enemy e : world.getEnemies()) {
            if (!e.isAlive()) continue;
            double d = e.distanceTo(px, py);
            if (d >= minDist) continue;

            double relX = e.getX() - px;
            double relY = e.getY() - py;
            double angleToEnemy = Math.atan2(relY, relX);
            double diff = angleToEnemy - angle;
            while (diff > Math.PI)  diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;

            if (Math.abs(diff) <= FIRE_FOV / 2.0) {
                minDist = d;
                closest = e;
            }
        }
        return closest;
    }

    // ────────────────────────────────────────────
    // UC7: Enemy AI Periodic Loop
    // ────────────────────────────────────────────

    public void periodicAiUpdate() {
        Player p = world.getPlayer();

        if (p == null || !p.isAlive()) return; // guard: scheduler fires before startGame()

        TeamUiPort uiPort;
        try {
            uiPort = TeamUiPort.getInstance();
        } catch (IllegalStateException e) {
            return; // UI not ready yet
        }

        // Advance reload timer; push ammo update when reload completes
        boolean wasReloading = p.isReloading();
        p.tickReload();
        if (wasReloading && !p.isReloading()) {
            uiPort.updateAmmo(p.getAmmo());
            uiPort.log("Reloaded! Ammo: " + p.getAmmo());
        }

        for (Enemy e : world.getEnemies()) {
            if (!e.isAlive()) continue;

            boolean isAttacking = e.updateAI(p.getX(), p.getY());
            uiPort.updateEnemy(e.getId(), e.getX(), e.getY());

            if (isAttacking) {
                p.takeDamage(ENEMY_DAMAGE);
                uiPort.updateHealth(p.getHealth());
                uiPort.log("Player hit by enemy " + e.getId() + "! HP: " + p.getHealth());

                if (!p.isAlive()) {
                    uiPort.log("Game Over! Final score: " + p.getScore());
                    return;
                }
            }
        }

        lastRemovedEnemies = world.removeDeadEnemies();
        for (Integer enemyId : lastRemovedEnemies) {
            uiPort.removeEnemy(enemyId);
        }

        processPickUpItem();
    }
}
