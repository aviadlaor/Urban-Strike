package ai.model;

public class WaveManager {

    public static final int TOTAL_WAVES = 5;
    private static final int[] ENEMIES_PER_WAVE = {2, 3, 4, 5, 6};

    private int currentWave = 0;   // 0 = לפני התחלה
    private boolean waitingForNext = false;
    private int waitTicks = 0;
    private static final int WAIT_TICKS_BETWEEN_WAVES = 10; // ~3 שניות ב-300ms

    public boolean tick(int aliveEnemies) {
        if (waitingForNext) {
            if (--waitTicks <= 0) {
                waitingForNext = false;
                return true; // signal: spawn next wave now
            }
            return false;
        }
        if (currentWave > 0 && aliveEnemies == 0) {
            if (currentWave >= TOTAL_WAVES) return false; // victory — no more waves
            waitingForNext = true;
            waitTicks = WAIT_TICKS_BETWEEN_WAVES;
        }
        return false;
    }

    public void startFirstWave() {
        currentWave = 1;
        waitingForNext = false;
    }

    public int nextWave() {
        currentWave++;
        return currentWave;
    }

    public int enemiesForWave(int wave) {
        int idx = Math.max(0, Math.min(wave - 1, ENEMIES_PER_WAVE.length - 1));
        return ENEMIES_PER_WAVE[idx];
    }

    public int getCurrentWave()       { return currentWave; }
    public boolean isWaitingForNext() { return waitingForNext; }
    public boolean isVictory()        { return currentWave >= TOTAL_WAVES; }
}
