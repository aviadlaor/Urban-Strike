package ai.control;

import ai.model.Enemy;
import ai.model.GameWorld;
import ai.model.Item;
import ai.model.Player;
import ai.model.SoundEngine;
import ai.model.WaveManager;
import shared.ui_ports.TeamUiPort;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

public class UrbanStrikeBackend {

    public static final int SCREEN_W = 1280;
    public static final int SCREEN_H = 720;
    public static final double MOVE_STEP = 15.0;
    public static final double ROTATE_STEP = Math.PI / 18; // 10 degrees per keypress
    public static final int BULLET_DAMAGE = 34;  // 3 hits to kill a 100-HP enemy
    public static final int ENEMY_DAMAGE = 10;
    public static final int SCORE_PER_KILL = 100;
    private static final int FIRE_RANGE = 400;

    private GameWorld world = new GameWorld();
    private List<Integer> lastRemovedEnemies = new ArrayList<>();
    private ai.ui.GamePanel panel;
    private boolean inCover = false;
    private boolean powerWeaponSpawned = false;
    private boolean waveSpawnPending   = false;

    public void setPanel(ai.ui.GamePanel panel) { this.panel = panel; }

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
            ((ai.ui.TeamUiPortImpl) uiPort).setGameWorld(player, world.getEnemies(), world.getCityElements());
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

        // התחל גל ראשון
        world.getWaveManager().startFirstWave();
        spawnWave(1);
    }

    private void spawnWave(int wave) {
        List<ai.model.Enemy> spawned = world.spawnWave(wave);
        TeamUiPort uiPort = TeamUiPort.getInstance();
        for (ai.model.Enemy e : spawned) {
            uiPort.addEnemy(e.getId(), e.getX(), e.getY());
        }
        uiPort.updateWave(wave, WaveManager.TOTAL_WAVES);
        uiPort.log("Wave " + wave + " started! Enemies: " + spawned.size());
    }

    // ────────────────────────────────────────────
    // US-08: Restart Game (full state reset)
    // ────────────────────────────────────────────

    public void restartGame() {
        inCover = false;
        powerWeaponSpawned = false;
        waveSpawnPending   = false;
        TeamUiPort.getInstance().triggerRestartGame();
        world = new GameWorld();
        startGame();
    }

    // ────────────────────────────────────────────
    // UC2: Move Player
    // ────────────────────────────────────────────

    public void setPlayerAngle(double angle) {
        Player p = world.getPlayer();
        p.setAngle(angle);
        TeamUiPort.getInstance().updatePlayer(p.getX(), p.getY());
    }

    public void rotatePlayer(double delta) {
        Player p = world.getPlayer();
        p.setAngle(p.getAngle() + delta);
        TeamUiPort.getInstance().updatePlayer(p.getX(), p.getY());
    }

    private boolean isNight    = false;
    private int     killStreak = 0;

    public void toggleNight() {
        isNight = !isNight;
        TeamUiPort.getInstance().setNightMode(isNight);
    }

    public void toggleCover() {
        inCover = !inCover;
        world.getPlayer().setCover(inCover);
        TeamUiPort.getInstance().updateCoverState(inCover);
        if (inCover) {
            boolean nearObject = world.isPlayerInCover(
                world.getPlayer().getX(), world.getPlayer().getY());
            TeamUiPort.getInstance().log(
                nearObject ? "Cover ON — protected!" : "Cover ON — move near object for protection");
            TeamUiPort.getInstance().showPickupMessage(
                nearObject ? "Protected!" : "Get near cover!");
        } else {
            TeamUiPort.getInstance().log("Cover OFF");
        }
    }

    public void movePlayer(String direction) {
        TeamUiPort.getInstance().updateMovingState(true);
        Player p = world.getPlayer();
        double step = inCover ? MOVE_STEP * 0.5 : MOVE_STEP;

        List<ai.model.CityElement> els = world.getCityElements();
        switch (direction) {
            case "UP":    p.moveForward (step, els); break;
            case "DOWN":  p.moveBackward(step, els); break;
            case "LEFT":  p.strafeLeft  (step, els); break;
            case "RIGHT": p.strafeRight (step, els); break;
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

        SoundEngine.playShoot();
        TeamUiPort.getInstance().updateAmmo(p.getAmmo());
        TeamUiPort.getInstance().showFireEffect();
        world.getStats().recordShot();

        int effectiveRange = inCover ? (int)(FIRE_RANGE * 1.1) : FIRE_RANGE;
        Enemy target = findClosestEnemyInSight(p.getX(), p.getY(), p.getAngle(), effectiveRange);
        if (target != null) {
            int damage = world.getPlayer().getWeapon().getDamage();
            target.takeDamage(damage);
            SoundEngine.playHit();
            world.getStats().recordHit();
            int healthPct = (int)((target.getHealth() / (double) Enemy.MAX_HEALTH) * 100);
            TeamUiPort.getInstance().updateEnemyHealth(target.getId(), healthPct);
            TeamUiPort.getInstance().showEnemyHit(target.getId(), target.getX(), target.getY());
            TeamUiPort.getInstance().log("Hit enemy " + target.getId() + " | HP left: " + target.getHealth());
            TeamUiPort.getInstance().updateEnemy(target.getId(), target.getX(), target.getY());

            if (!target.isAlive()) {
                SoundEngine.playEnemyDeath();
                p.addScore(SCORE_PER_KILL);
                world.getStats().recordKill();
                killStreak++;
                TeamUiPort uiPort = TeamUiPort.getInstance();
                if      (killStreak == 2) uiPort.showKillStreak("DOUBLE KILL!", killStreak);
                else if (killStreak == 3) uiPort.showKillStreak("TRIPLE KILL!", killStreak);
                else if (killStreak >= 5) uiPort.showKillStreak("RAMPAGE!!",    killStreak);
                uiPort.updateScore(p.getScore());
                uiPort.showEnemyDeath(target.getId(), target.getX(), target.getY());
                uiPort.removeEnemy(target.getId());
                uiPort.log("Enemy " + target.getId() + " killed! Score: " + p.getScore());

                ai.model.Item dropped = world.tryDropItem(target.getX(), target.getY());
                if (dropped != null) {
                    TeamUiPort.getInstance().addItem(
                        dropped.getId(),
                        dropped.getPosition().getX(),
                        dropped.getPosition().getY(),
                        dropped.getItemType());
                    TeamUiPort.getInstance().log("Item dropped: " + dropped.getItemType());
                }
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
        boolean pickedUpAnything = false;
        for (Item item : world.getItems()) {
            if (item.isCollected()) continue;
            if (item.getPosition().distanceTo(p.getX(), p.getY()) <= 60) {
                item.markAsCollected();
                SoundEngine.playPickup();
                pickedUpAnything = true;
                if ("Medkit".equals(item.getItemType())) {
                    p.pickupHealth(30);
                    uiPort.updateHealth(p.getHealth());
                } else if ("PowerWeapon".equals(item.getItemType())) {
                    p.getWeapon().setPowered(true);
                    uiPort.updatePoweredState(true);
                    uiPort.showMessage("POWER WEAPON ACTIVATED!");
                    SoundEngine.playPickup();
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
        if (pickedUpAnything && uiPort instanceof ai.ui.TeamUiPortImpl) {
            ((ai.ui.TeamUiPortImpl) uiPort).setNearbyItem("");
        }
    }

    // ────────────────────────────────────────────
    // UC: Reload Weapon
    // ────────────────────────────────────────────

    public void reloadWeapon() {
        Player p = world.getPlayer();
        if (p == null) return;
        if (p.reload()) {
            SoundEngine.playReload();
            TeamUiPort.getInstance().log("Reloading...");
            TeamUiPort.getInstance().updateAmmo(p.getAmmo());
            Timer reloadAnim = new Timer(30, null);
            final long[] start = {System.currentTimeMillis()};
            final int RELOAD_MS = 1200;
            reloadAnim.addActionListener(e -> {
                double progress = (System.currentTimeMillis() - start[0]) / (double) RELOAD_MS;
                TeamUiPort.getInstance().updateReloadAnimation(Math.min(1.0, progress));
                if (progress >= 1.0) reloadAnim.stop();
            });
            reloadAnim.start();
        }
    }

    private static final double FIRE_FOV = Math.PI / 3; // matches Raycaster FOV

    private void spawnPowerWeapon() {
        double px = world.getPlayer().getX();
        double py = world.getPlayer().getY();
        double wx = px + 80;
        double wy = py + 80;
        int id = 9999;
        Item item = new Item(id, "PowerWeapon", new ai.model.Position(wx, wy));
        world.getItems().add(item);
        TeamUiPort.getInstance().addItem(id, wx, wy, "PowerWeapon");
        TeamUiPort.getInstance().showMessage("POWER WEAPON DROPPED — Press E to pick up!");
        TeamUiPort.getInstance().log("PowerWeapon spawned at " + wx + "," + wy);
    }

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

            int attackType = e.updateAI(p.getX(), p.getY());

            if (attackType > 0 && inCover) {
                boolean playerInCover = world.isPlayerInCover(p.getX(), p.getY());
                boolean los = world.hasLineOfSight(e.getX(), e.getY(),
                                                    p.getX(), p.getY());
                if (playerInCover && !los) {
                    attackType = 0;
                    uiPort.log("Enemy " + e.getId() + " — line of sight blocked");
                }
            }

            uiPort.updateEnemy(e.getId(), e.getX(), e.getY());

            if (attackType == 1) {
                uiPort.showEnemyAttack(e.getId());
                p.takeDamage(ENEMY_DAMAGE);
                SoundEngine.playPlayerHit();
                killStreak = 0;
                uiPort.updateHealth(p.getHealth());
                uiPort.log("Player hit (melee) by enemy " + e.getId() + "! HP: " + p.getHealth());
            } else if (attackType == 2) {
                uiPort.showEnemyAttack(e.getId());
                p.takeDamage(ENEMY_DAMAGE / 2);
                SoundEngine.playPlayerHit();
                killStreak = 0;
                uiPort.updateHealth(p.getHealth());
                uiPort.log("Player hit (ranged) by enemy " + e.getId() + "! HP: " + p.getHealth());
            }

            if (attackType > 0 && !p.isAlive()) {
                uiPort.triggerGameOverScreen(p.getScore(), world.getStats());
                if (panel != null) panel.setGameOver(true);
                return;
            }
        }

        lastRemovedEnemies = world.removeDeadEnemies();
        for (Integer enemyId : lastRemovedEnemies) {
            uiPort.removeEnemy(enemyId);
        }

        // Wave progression
        WaveManager wm = world.getWaveManager();
        int alive = world.getEnemies().size();
        if (!waveSpawnPending) {
            boolean spawnNext = wm.tick(alive);
            if (spawnNext) {
                int nextWave = wm.nextWave();
                waveSpawnPending = true;
                uiPort.showWaveIncoming(nextWave);
                Timer spawnDelay = new Timer(3000, e -> {
                    spawnWave(nextWave);
                    waveSpawnPending = false;
                });
                spawnDelay.setRepeats(false);
                spawnDelay.start();
            } else if (wm.isVictory() && alive == 0) {
                uiPort.triggerVictoryScreen(p.getScore(), world.getStats());
                if (panel != null) panel.setGameOver(true);
            }
        }

        // Power Weapon drop — end of wave 2
        if (wm.getCurrentWave() == 2 && alive == 0
                && !world.getPlayer().getWeapon().isPowered()
                && !powerWeaponSpawned) {
            powerWeaponSpawned = true;
            spawnPowerWeapon();
        }

        // Proximity detection only — actual pickup requires player to press E
        String nearby = "";
        for (Item item : world.getItems()) {
            if (!item.isCollected() && item.getPosition().distanceTo(p.getX(), p.getY()) <= 60) {
                nearby = item.getItemType();
                break;
            }
        }
        if (uiPort instanceof ai.ui.TeamUiPortImpl) {
            ((ai.ui.TeamUiPortImpl) uiPort).setNearbyItem(nearby);
        }
    }
}
