package ai.model;

public class GameStats {
    private int  kills      = 0;
    private int  shotsFired = 0;
    private int  shotsHit   = 0;
    private long startTime  = System.currentTimeMillis();

    public void recordKill()  { kills++; }
    public void recordShot()  { shotsFired++; }
    public void recordHit()   { shotsHit++; }

    public int getKills()      { return kills; }
    public int getShotsFired() { return shotsFired; }
    public int getShotsHit()   { return shotsHit; }
    public int getAccuracy()   {
        return shotsFired == 0 ? 0 : (int)(shotsHit * 100.0 / shotsFired);
    }
    public int getElapsedSeconds() {
        return (int)((System.currentTimeMillis() - startTime) / 1000);
    }
    public String getTimeString() {
        int sec = getElapsedSeconds();
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
    public void reset() {
        kills = 0; shotsFired = 0; shotsHit = 0;
        startTime = System.currentTimeMillis();
    }
}
