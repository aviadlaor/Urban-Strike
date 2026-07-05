package shared.ui_ports;

public abstract class TeamUiPort {

    private static TeamUiPort instance;

    public static void setInstance(TeamUiPort ui) {
        if (ui == null) throw new IllegalArgumentException("TeamUiPort instance cannot be null");
        if (instance != null) throw new IllegalStateException("TeamUiPort instance already set");
        instance = ui;
    }

    public static TeamUiPort getInstance() {
        if (instance == null) throw new IllegalStateException("TeamUiPort instance not set yet");
        return instance;
    }

    // הפונקציות המקוריות של המרצה
    public abstract void method1(int id);
    public abstract void log(String message);

    // --- הפונקציות של Urban Strike ---
    public abstract void showPlayer(double x, double y);
    public abstract void updatePlayer(double x, double y);
    public abstract void addEnemy(int id, double x, double y);
    public abstract void updateEnemy(int id, double x, double y);
    public abstract void removeEnemy(int id);
    public abstract void updateHealth(int health);
    public abstract void updateScore(int score);
    public abstract void updateAmmo(int ammo);
    public abstract void showFireEffect();
    public abstract void showMessage(String msg);

    // --- Item pickups ---
    public abstract void addItem(int id, double x, double y, String type);
    public abstract void removeItem(int id);
    public abstract void showPickupMessage(String msg);

    // --- US-08 Game Over / US-02 enemy hit feedback ---
    public abstract void triggerGameOverScreen(int finalScore, ai.model.GameStats stats);
    public abstract void triggerRestartGame();
    public abstract void updateEnemyHealth(int enemyId, int healthPercent);

    // --- Wave system / Victory ---
    public abstract void updateWave(int wave, int totalWaves);
    public abstract void triggerVictoryScreen(int finalScore, ai.model.GameStats stats);

    // --- Cover ---
    public abstract void updateCoverState(boolean inCover);

    // --- Enemy fire feedback ---
    public abstract void showEnemyAttack(int enemyId);

    // --- Visual effects ---
    public abstract void showEnemyHit(int enemyId, double enemyX, double enemyY);
    public abstract void showEnemyDeath(int enemyId, double x, double y);
    public abstract void setNightMode(boolean night);

    // --- Killstreak + Wave ---
    public abstract void showKillStreak(String message, int count);
    public abstract void showWaveIncoming(int wave);

    // --- Power Weapon ---
    public abstract void updatePoweredState(boolean powered);

    // --- Bob + Reload animation ---
    public abstract void updateMovingState(boolean moving);
    public abstract void updateReloadAnimation(double progress);
}
//check