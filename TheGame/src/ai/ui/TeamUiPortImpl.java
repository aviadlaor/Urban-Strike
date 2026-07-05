package ai.ui;

import ai.model.CityElement;
import ai.model.Enemy;
import ai.model.Player;
import ai.model.WaveManager;
import shared.ui_ports.TeamUiPort;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TeamUiPortImpl extends TeamUiPort {

    private static final int    SCREEN_W   = 1280;
    private static final int    SCREEN_H   = 720;
    private static final double FOV        = Math.PI / 3;
    private static final double WORLD_W    = 1280.0;
    private static final double WORLD_H    = 720.0;
    private static final double PROJ_SCALE = 130_000.0;
    private static final int    TEX_SIZE   = 64;

    // ── Dynamic render dimensions (updated each frame) ───────────────────────
    private int renderW = SCREEN_W;
    private int renderH = SCREEN_H;

    // ── Push-based state from backend ────────────────────────────────────────
    private double  playerX, playerY;
    private Map<Integer, double[]> enemies   = new HashMap<>();
    private Map<Integer, double[]> itemPos   = new HashMap<>();
    private Map<Integer, String>   itemTypes = new HashMap<>();
    private int     health        = 100;
    private int     score         = 0;
    private int     ammo          = 30;
    private String  message       = "";
    private boolean showFireFlash      = false;
    private boolean showBulletTracer   = false;
    private int     weaponRecoil       = 0;
    private boolean inCover            = false;
    private boolean showHitFlash       = false;
    private int     muzzleFlashEnemyId = -1;
    private boolean showIncoming       = false;

    // ── US-08 Game Over + US-02 per-enemy health feedback ────────────────────
    private boolean gameOver    = false;
    private boolean victory     = false;
    private int     finalScore  = 0;
    private int     currentWave = 0;
    private final Map<Integer, Integer> enemyHealthPercent = new HashMap<>();

    // ── Item flash animation ──────────────────────────────────────────────────
    private boolean itemFlashState = false;
    private javax.swing.Timer itemFlashTimer;

    // ── Walk animation ────────────────────────────────────────────────────────
    private long walkAnimMs = 0;

    // ── Visual effects ────────────────────────────────────────────────────────
    private final List<int[]>    bloodParticles = new ArrayList<>();
    private final List<float[]>  smokeParticles = new ArrayList<>();
    private final List<double[]> dyingEnemies   = new ArrayList<>();
    private boolean isNight = false;

    // ── Killstreak + Wave + End stats ─────────────────────────────────────────
    private String  killStreakMsg    = "";
    private int     killStreakCount  = 0;
    private boolean showWaveScreen   = false;
    private int     incomingWave     = 0;
    private int     waveCountdown    = 3;
    private ai.model.GameStats endStats = null;

    // ── Manual item pickup prompt ─────────────────────────────────────────────
    private String nearbyItemName = "";

    // ── Title screen ─────────────────────────────────────────────────────────
    private boolean showTitleScreen = true;

    // ── Power Weapon ──────────────────────────────────────────────────────────
    private boolean weaponPowered = false;

    // ── Bob + Reload animation ────────────────────────────────────────────────
    private boolean playerMoving   = false;
    private double  bobPhase       = 0;
    private boolean isReloading    = false;
    private double  reloadProgress = 0;

    // ── Procedural textures + pixel-buffer ───────────────────────────────────
    private final int[][] wallTex = new int[TEX_SIZE][TEX_SIZE];
    private BufferedImage offscreen = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);

    // ── Direct model references set once at startGame ────────────────────────
    private final JPanel    panel;
    private Player          gamePlayer;
    private List<Enemy>     gameEnemies     = new ArrayList<>();
    private List<CityElement> gameCityElements = new ArrayList<>();

    public TeamUiPortImpl(JPanel panel) {
        this.panel = panel;
        generateWallTexture();
        itemFlashTimer = new Timer(300, e -> {
            itemFlashState = !itemFlashState;
            panel.repaint();
        });
        itemFlashTimer.start();
    }

    private void generateWallTexture() {
        Random rng = new Random(42);
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                int brickRow = y / 8;
                boolean mortarH = (y % 8 == 0);
                boolean mortarV = ((x + brickRow * 16) % 32 == 0);

                if (mortarH || mortarV) {
                    int v = 160 + rng.nextInt(20);
                    wallTex[y][x] = (v << 16) | (v << 8) | v;
                } else {
                    int posInBrick = y % 8;
                    float shade = 1.0f - (posInBrick / 8.0f) * 0.12f;
                    int r = Math.min(255, (int)((195 + rng.nextInt(30)) * shade));
                    int g = Math.min(255, (int)((105 + rng.nextInt(20)) * shade));
                    int b = Math.min(255, (int)((80  + rng.nextInt(15)) * shade));
                    wallTex[y][x] = (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    public void setGameWorld(Player player, List<Enemy> enemies,
                             List<CityElement> cityElements) {
        this.gamePlayer       = player;
        this.gameEnemies      = enemies;
        this.gameCityElements = cityElements;
    }

    // ── TeamUiPort interface ─────────────────────────────────────────────────

    @Override public void method1(int id) {}

    @Override public void showPlayer(double x, double y)   { playerX = x; playerY = y; panel.repaint(); }
    @Override public void updatePlayer(double x, double y) { playerX = x; playerY = y; panel.repaint(); }

    @Override public void addEnemy(int id, double x, double y) {
        enemies.put(id, new double[]{x, y}); panel.repaint();
    }

    @Override public void updateEnemy(int id, double x, double y) {
        double[] pos = enemies.get(id);
        if (pos != null) { pos[0] = x; pos[1] = y; panel.repaint(); }
    }

    @Override public void removeEnemy(int id)  { enemies.remove(id); panel.repaint(); }
    @Override public void updateHealth(int h)  { this.health = h; panel.repaint(); }
    @Override public void updateScore(int s)   { this.score  = s; panel.repaint(); }
    @Override public void updateAmmo(int a)    { this.ammo   = a; panel.repaint(); }

    @Override public void showFireEffect() {
        showFireFlash    = true;
        showBulletTracer = true;
        weaponRecoil     = 18;
        panel.repaint();
        Timer t = new Timer(80, e -> {
            showFireFlash    = false;
            showBulletTracer = false;
            panel.repaint();
        });
        t.setRepeats(false); t.start();

        Random rng = new Random();
        for (int i = 0; i < 4; i++) {
            smokeParticles.add(new float[]{ 0, 0, 6 + rng.nextInt(8), 180 });
        }
        Timer smoke = new Timer(50, null);
        smoke.addActionListener(e -> {
            smokeParticles.replaceAll(s -> {
                s[1] -= 2; s[2] += 1.5f; s[3] -= 25; return s;
            });
            smokeParticles.removeIf(s -> s[3] <= 0);
            panel.repaint();
            if (smokeParticles.isEmpty()) smoke.stop();
        });
        smoke.start();
    }

    @Override public void showMessage(String msg) {
        this.message = msg; panel.repaint();
        Timer t = new Timer(2000, e -> { this.message = ""; panel.repaint(); });
        t.setRepeats(false); t.start();
    }

    @Override public void log(String message) { System.out.println("[Game] " + message); }

    @Override public void addItem(int id, double x, double y, String type) {
        itemPos.put(id, new double[]{x, y});
        itemTypes.put(id, type);
        panel.repaint();
    }

    @Override public void removeItem(int id) {
        itemPos.remove(id);
        itemTypes.remove(id);
        panel.repaint();
    }

    @Override public void showPickupMessage(String msg) { showMessage(msg); }

    // ── US-08: Game Over ─────────────────────────────────────────────────────

    @Override public void triggerGameOverScreen(int finalScore, ai.model.GameStats stats) {
        this.gameOver   = true;
        this.finalScore = finalScore;
        this.endStats   = stats;
        panel.repaint();
    }

    @Override public void triggerRestartGame() {
        this.gameOver       = false;
        this.victory        = false;
        this.inCover        = false;
        this.currentWave    = 0;
        this.health         = 100;
        this.ammo           = 30;
        this.score          = 0;
        this.nearbyItemName  = "";
        this.killStreakMsg   = "";
        this.showWaveScreen  = false;
        this.endStats        = null;
        this.weaponPowered   = false;
        this.showTitleScreen = false;
        enemyHealthPercent.clear();
        panel.repaint();
    }

    @Override public void updateWave(int wave, int totalWaves) {
        this.currentWave = wave;
        panel.repaint();
    }

    @Override public void triggerVictoryScreen(int finalScore, ai.model.GameStats stats) {
        this.victory    = true;
        this.finalScore = finalScore;
        this.endStats   = stats;
        panel.repaint();
    }

    @Override public void updateCoverState(boolean inCover) {
        this.inCover = inCover;
        panel.repaint();
    }

    @Override public void showEnemyAttack(int enemyId) {
        showHitFlash       = true;
        muzzleFlashEnemyId = enemyId;
        showIncoming       = true;
        panel.repaint();

        Timer t = new Timer(150, e -> {
            showHitFlash = false;
            showIncoming = false;
            panel.repaint();
        });
        t.setRepeats(false); t.start();

        Timer muzzle = new Timer(80, e -> {
            muzzleFlashEnemyId = -1;
            panel.repaint();
        });
        muzzle.setRepeats(false); muzzle.start();
    }

    @Override public void showEnemyHit(int enemyId, double enemyX, double enemyY) {
        Random rng = new Random();
        for (int i = 0; i < 7; i++) {
            bloodParticles.add(new int[]{
                (int)enemyX + rng.nextInt(30) - 15,
                (int)enemyY + rng.nextInt(30) - 15,
                3 + rng.nextInt(5), 255
            });
        }
        Timer bt = new Timer(300, e -> { bloodParticles.clear(); panel.repaint(); });
        bt.setRepeats(false); bt.start();
        panel.repaint();
    }

    @Override public void showEnemyDeath(int enemyId, double x, double y) {
        double[] dying = {x, y, 0, 255};
        dyingEnemies.add(dying);
        Timer dt = new Timer(30, null);
        dt.addActionListener(e -> {
            dying[2] += 9;
            dying[3] -= 15;
            panel.repaint();
            if (dying[3] <= 0) { dyingEnemies.remove(dying); dt.stop(); }
        });
        dt.start();
    }

    @Override public void setNightMode(boolean night) {
        this.isNight = night;
        panel.repaint();
    }

    @Override public void showKillStreak(String message, int count) {
        killStreakMsg   = message;
        killStreakCount = count;
        panel.repaint();
        Timer t = new Timer(2000, e -> { killStreakMsg = ""; panel.repaint(); });
        t.setRepeats(false); t.start();
    }

    @Override public void showWaveIncoming(int wave) {
        showWaveScreen = true;
        incomingWave   = wave;
        waveCountdown  = 3;
        panel.repaint();
        Timer countdown = new Timer(1000, null);
        countdown.addActionListener(e -> {
            waveCountdown--;
            panel.repaint();
            if (waveCountdown <= 0) { showWaveScreen = false; panel.repaint(); countdown.stop(); }
        });
        countdown.start();
    }

    @Override public void updatePoweredState(boolean powered) {
        this.weaponPowered = powered;
        panel.repaint();
    }

    @Override public void updateMovingState(boolean moving) {
        this.playerMoving = moving;
    }

    @Override public void updateReloadAnimation(double progress) {
        this.isReloading    = (progress < 1.0);
        this.reloadProgress = progress;
        panel.repaint();
    }

    // ── Manual item pickup: called from backend via cast ─────────────────────

    public void setNearbyItem(String itemName) {
        this.nearbyItemName = (itemName == null) ? "" : itemName;
        panel.repaint();
    }

    // ── US-02: visual hit feedback ───────────────────────────────────────────

    @Override public void updateEnemyHealth(int enemyId, int healthPercent) {
        enemyHealthPercent.put(enemyId, healthPercent);
        panel.repaint();
    }

    // ── Entry point called by GamePanel.paintComponent ───────────────────────

    public void render(Graphics g) {
        renderW = Math.max(1, panel.getWidth());
        renderH = Math.max(1, panel.getHeight());

        walkAnimMs = System.currentTimeMillis();

        if (playerMoving) {
            bobPhase += 0.12;
            if (bobPhase > Math.PI * 2) bobPhase -= Math.PI * 2;
        } else {
            bobPhase *= 0.85;
        }
        playerMoving = false;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (showTitleScreen) { renderTitleScreen(g2); return; }
        if (victory)  { renderVictory(g2);  return; }
        if (gameOver) { renderGameOver(g2); return; }

        if (gamePlayer == null) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, renderW, renderH);
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("Arial", Font.PLAIN, 18));
            g2.drawString("Waiting for game state...", 20, 50);
            return;
        }

        renderWorld(g2);
        renderHUD(g2);
    }

    // ── Title screen ─────────────────────────────────────────────────────────

    public void hideTitleScreen() {
        showTitleScreen = false;
        panel.repaint();
    }

    private void renderTitleScreen(Graphics2D g2) {
        int W = renderW, H = renderH;

        // night sky background
        g2.setColor(new Color(8, 10, 22));
        g2.fillRect(0, 0, W, H);

        // stars
        Random rng = new Random(77);
        for (int i = 0; i < 120; i++) {
            int sx = rng.nextInt(W);
            int sy = rng.nextInt(H * 2 / 3);
            int ss = rng.nextInt(2) + 1;
            g2.setColor(new Color(200 + rng.nextInt(55),
                                  200 + rng.nextInt(55),
                                  200 + rng.nextInt(55), 180));
            g2.fillOval(sx, sy, ss, ss);
        }

        // city silhouette
        g2.setColor(new Color(15, 15, 25));
        int[] buildingX = {0, 0, 80, 80, 140, 140, 200, 200, 260,
                            260, 320, 320, 400, 400, 480, 480,
                            560, 560, 640, 640, 720, 720, 800,
                            800, 880, 880, 960, 960, 1040, 1040,
                            1120, 1120, 1200, 1200, 1280, 1280};
        int[] buildingY = {H, H*2/3+60, H*2/3+60, H*2/3+20, H*2/3+20,
                            H*2/3+50, H*2/3+50, H*2/3+10, H*2/3+10,
                            H*2/3+40, H*2/3+40, H*2/3, H*2/3,
                            H*2/3+30, H*2/3+30, H*2/3+15, H*2/3+15,
                            H*2/3+45, H*2/3+45, H*2/3+5, H*2/3+5,
                            H*2/3+35, H*2/3+35, H*2/3+25, H*2/3+25,
                            H*2/3+55, H*2/3+55, H*2/3+8, H*2/3+8,
                            H*2/3+42, H*2/3+42, H*2/3+18, H*2/3+18,
                            H*2/3+60, H*2/3+60, H};
        g2.fillPolygon(buildingX, buildingY, buildingX.length);

        // horizon glow
        g2.setColor(new Color(180, 60, 10, 60));
        g2.fillRect(0, H*2/3 - 30, W, 90);
        g2.setColor(new Color(220, 100, 20, 30));
        g2.fillRect(0, H*2/3 - 60, W, 60);

        // dark ground
        g2.setColor(new Color(12, 12, 18));
        g2.fillRect(0, H*2/3 + 60, W, H);

        // URBAN
        g2.setFont(new Font("Arial", Font.BOLD, 110));
        g2.setColor(new Color(220, 60, 40));
        String urban = "URBAN";
        int uw = g2.getFontMetrics().stringWidth(urban);
        g2.drawString(urban, W/2 - uw/2, H/2 - 30);

        // STRIKE
        g2.setFont(new Font("Arial", Font.BOLD, 110));
        g2.setColor(Color.WHITE);
        String strike = "STRIKE";
        int sw2 = g2.getFontMetrics().stringWidth(strike);
        g2.drawString(strike, W/2 - sw2/2, H/2 + 85);

        // divider
        g2.setColor(new Color(220, 60, 40));
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(W/2 - 200, H/2 + 100, W/2 + 200, H/2 + 100);

        // subtitle
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(new Color(160, 160, 160));
        String sub = "Urban Warfare Simulation";
        int subW = g2.getFontMetrics().stringWidth(sub);
        g2.drawString(sub, W/2 - subW/2, H/2 + 130);

        // blinking "Press ENTER"
        long now = System.currentTimeMillis();
        if ((now / 600) % 2 == 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 24));
            g2.setColor(Color.WHITE);
            String prompt = "Press ENTER to Start";
            int pw = g2.getFontMetrics().stringWidth(prompt);
            g2.drawString(prompt, W/2 - pw/2, H*3/4 + 20);
        }

        // controls hint
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.setColor(new Color(100, 100, 100));
        String controls = "WASD — Move   |   Mouse — Aim   |   LMB — Fire   |   R — Reload   |   E — Pickup   |   C — Cover   |   ESC — Exit";
        int cw = g2.getFontMetrics().stringWidth(controls);
        g2.drawString(controls, W/2 - cw/2, H - 30);

        panel.repaint();
    }

    // ── Full-screen overlays ──────────────────────────────────────────────────

    private void renderVictory(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, renderW, renderH);
        drawCentered(g2, "MISSION COMPLETE",           new Font("Arial", Font.BOLD,  72), new Color(50, 220, 80), renderH / 2 - 120);
        drawCentered(g2, "FINAL SCORE: " + finalScore, new Font("Arial", Font.BOLD,  36), Color.WHITE,            renderH / 2 - 50);
        if (endStats != null) drawEndStats(g2);
        drawCentered(g2, "Press R to Play Again",      new Font("Arial", Font.PLAIN, 22), Color.LIGHT_GRAY,       renderH / 2 + 150);
    }

    private void renderGameOver(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, renderW, renderH);
        drawCentered(g2, "GAME OVER",                  new Font("Arial", Font.BOLD,  72), Color.RED,              renderH / 2 - 120);
        drawCentered(g2, "FINAL SCORE: " + finalScore, new Font("Arial", Font.BOLD,  36), Color.WHITE,            renderH / 2 - 50);
        if (endStats != null) drawEndStats(g2);
        drawCentered(g2, "Press R to Restart",         new Font("Arial", Font.PLAIN, 22), Color.LIGHT_GRAY,       renderH / 2 + 150);
    }

    private void drawEndStats(Graphics2D g2) {
        int y    = renderH / 2 + 10;
        int col1 = renderW / 2 - 180;
        int col2 = renderW / 2 + 20;
        g2.setFont(new Font("Arial", Font.PLAIN, 20));

        g2.setColor(new Color(180, 180, 180));
        g2.drawString("Enemies killed:", col1, y);
        g2.setColor(Color.WHITE);
        g2.drawString(String.valueOf(endStats.getKills()), col2, y);

        y += 32;
        g2.setColor(new Color(180, 180, 180));
        g2.drawString("Shots fired:", col1, y);
        g2.setColor(Color.WHITE);
        g2.drawString(String.valueOf(endStats.getShotsFired()), col2, y);

        y += 32;
        g2.setColor(new Color(180, 180, 180));
        g2.drawString("Accuracy:", col1, y);
        g2.setColor(endStats.getAccuracy() > 50 ? new Color(50, 200, 80) : new Color(220, 180, 0));
        g2.drawString(endStats.getAccuracy() + "%", col2, y);

        y += 32;
        g2.setColor(new Color(180, 180, 180));
        g2.drawString("Time:", col1, y);
        g2.setColor(Color.WHITE);
        g2.drawString(endStats.getTimeString(), col2, y);
    }

    private void drawCentered(Graphics2D g2, String text, Font font, Color color, int y) {
        g2.setFont(font);
        g2.setColor(color);
        int textW = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (renderW - textW) / 2, y);
    }

    // ── Phase A: FPS raycasted world ─────────────────────────────────────────

    private void renderWorld(Graphics2D g2) {
        int W = renderW, H = renderH;

        // Recreate pixel buffer if window was resized
        if (offscreen == null || offscreen.getWidth() != W || offscreen.getHeight() != H) {
            offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        }

        double px = gamePlayer.getX();
        double py = gamePlayer.getY();
        double pa = gamePlayer.getAngle();

        double projScale = inCover ? PROJ_SCALE * 0.75 : PROJ_SCALE;

        int[] pixels     = ((DataBufferInt) offscreen.getRaster().getDataBuffer()).getData();
        int[] wallHeights = new int[W];
        int[] floorYArr   = new int[W];

        for (int col = 0; col < W; col++) {
            double rayAngle = pa - FOV / 2.0 + (col / (double) W) * FOV;
            double[] hit    = castRayFull(px, py, rayAngle);
            double perpDist = Math.max(1.0, hit[0] * Math.cos(rayAngle - pa));

            int wallH   = Math.min(H, (int)(projScale / perpDist));
            wallHeights[col] = wallH;
            int wallTop = (H - wallH) / 2;
            int wallBot = wallTop + wallH;
            floorYArr[col] = wallBot;

            int texX = Math.min(TEX_SIZE - 1, (int)(hit[1] * TEX_SIZE));
            float fog = Math.min(1.0f, (float)(perpDist / 900.0));

            for (int y = 0; y < H; y++) {
                int packed;
                if (y < wallTop) {
                    float t = (wallTop > 0) ? (float) y / wallTop : 0f;
                    int r, gv, b;
                    if (isNight) {
                        r  = (int)(2  + 10 * t);
                        gv = (int)(2  + 8  * t);
                        b  = (int)(15 + 50 * t);
                        int starHash = (col * 2731 + y * 5171) & 0xFFFF;
                        if (starHash < 20 && t < 0.5f) { r = 220; gv = 220; b = 220; }
                    } else {
                        r  = (int)(100 + 80 * t);
                        gv = (int)(160 + 70 * t);
                        b  = (int)(220 + 35 * t);
                        r  = Math.min(255, r);
                        gv = Math.min(255, gv);
                        b  = Math.min(255, b);
                        int cloudHash = (col * 1371 + y * 971) & 0xFFFF;
                        if (cloudHash < 800 && t < 0.4f) {
                            float cBlend = (0.4f - t) / 0.4f * 0.7f;
                            r  = Math.min(255, r  + (int)(cBlend * (255 - r)));
                            gv = Math.min(255, gv + (int)(cBlend * (255 - gv)));
                            b  = Math.min(255, b  + (int)(cBlend * (255 - b)));
                        }
                    }
                    packed = (Math.min(255,r) << 16) | (Math.min(255,gv) << 8) | Math.min(255,b);
                } else if (y >= wallBot) {
                    float t = (H > wallBot) ? (float)(y - wallBot) / (H - wallBot) : 1f;

                    int baseR = (int)(85  + 40 * (1-t));
                    int baseG = (int)(75  + 35 * (1-t));
                    int baseB = (int)(65  + 30 * (1-t));

                    int noise = ((col * 3 + y * 7) & 0x7) - 4;
                    baseR = Math.max(0, Math.min(255, baseR + noise));
                    baseG = Math.max(0, Math.min(255, baseG + noise));
                    baseB = Math.max(0, Math.min(255, baseB + noise));

                    int crackHash = (col * 1237 + y * 3571) & 0x3FF;
                    if (crackHash < 3) {
                        baseR = (int)(baseR * 0.7f);
                        baseG = (int)(baseG * 0.7f);
                        baseB = (int)(baseB * 0.7f);
                    }

                    if (t < 0.12f) {
                        float fogBlend = (0.12f - t) / 0.12f * 0.8f;
                        int horizR = 150, horizG = 170, horizB = 200;
                        baseR = (int)(baseR * (1-fogBlend) + horizR * fogBlend);
                        baseG = (int)(baseG * (1-fogBlend) + horizG * fogBlend);
                        baseB = (int)(baseB * (1-fogBlend) + horizB * fogBlend);
                    }

                    packed = (Math.max(0, Math.min(255, baseR)) << 16)
                           | (Math.max(0, Math.min(255, baseG)) << 8)
                           |  Math.max(0, Math.min(255, baseB));
                } else {
                    int texY = Math.min(TEX_SIZE - 1, (int)((y - wallTop) * TEX_SIZE / (double) wallH));
                    int c  = wallTex[texY][texX];
                    int r  = (int)(((c >> 16) & 0xFF) * (1 - fog) + 200 * fog);
                    int gv = (int)(((c >>  8) & 0xFF) * (1 - fog) + 210 * fog);
                    int b  = (int)(( c        & 0xFF) * (1 - fog) + 220 * fog);
                    packed = (r << 16) | (gv << 8) | b;
                }
                pixels[y * W + col] = packed;
            }
        }

        g2.drawImage(offscreen, 0, 0, null);
        drawEnemySprites(g2, px, py, pa, wallHeights);
        drawItemSprites(g2, px, py, pa, wallHeights, floorYArr);
        drawCityElements(g2, px, py, pa, wallHeights);

        // Bullet tracer
        if (showBulletTracer) {
            Graphics2D tg = (Graphics2D) g2.create();
            tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            tg.setColor(new Color(255, 240, 180, 220));
            tg.setStroke(new BasicStroke(1.5f));
            int cx = W / 2, cy = H / 2;
            tg.drawLine(cx + 80, H - 80, cx, cy);
            tg.dispose();
        }

        // Fire flash
        if (showFireFlash) {
            g2.setColor(new Color(255, 200, 0, 200));
            g2.setStroke(new BasicStroke(3f));
            int cx = W / 2, cy = H / 2;
            g2.drawLine(cx - 16, cy, cx + 16, cy);
            g2.drawLine(cx, cy - 16, cx, cy + 16);
        }

        if (showHitFlash) {
            g2.setColor(new Color(220, 0, 0, 80));
            g2.fillRect(0, 0, W, H);
        }

        drawBloodParticles(g2, px, py, pa, W, H);
        if (isNight) drawLampostLights(g2, px, py, pa, W, H);
        drawWeapon(g2, W, H);
    }

    private void drawWeapon(Graphics2D g2, int W, int H) {
        if (weaponPowered) { drawPowerWeapon(g2, W, H); return; }
        int bobX = (int)(Math.sin(bobPhase) * 5);
        int bobY = (int)(Math.abs(Math.sin(bobPhase)) * 6);
        int reloadOffset = 0;
        if (isReloading) {
            if (reloadProgress < 0.5) {
                reloadOffset = (int)(reloadProgress * 2 * 180);
            } else {
                reloadOffset = (int)((1.0 - reloadProgress) * 2 * 180);
            }
        }
        int baseY = H - 20 - weaponRecoil + bobY + reloadOffset;
        int cx    = W / 2 + 120 + bobX;

        // ידית
        g2.setColor(new Color(90, 58, 32));
        g2.fillRoundRect(cx - 14, baseY - 80, 28, 75, 8, 8);

        // גוף נשק
        g2.setColor(new Color(50, 50, 52));
        g2.fillRoundRect(cx - 26, baseY - 135, 52, 65, 6, 6);
        g2.setColor(new Color(75, 75, 80));
        g2.fillRect(cx - 22, baseY - 130, 18, 55);

        // מחסנית
        g2.setColor(new Color(65, 52, 30));
        g2.fillRoundRect(cx - 8, baseY - 80, 16, 35, 4, 4);

        // קנה
        g2.setColor(new Color(28, 28, 28));
        g2.fillRect(cx - 10, baseY - 185, 18, 55);
        g2.setColor(new Color(55, 55, 55));
        g2.fillRect(cx - 7, baseY - 183, 5, 50);

        // כוון
        g2.setColor(new Color(22, 22, 22));
        g2.fillRect(cx - 14, baseY - 148, 28, 8);

        // עשן מפה הקנה
        int muzzleX = cx - 10;
        int muzzleY = baseY - 185;
        for (float[] sm : new ArrayList<>(smokeParticles)) {
            int alpha = Math.max(0, Math.min(255, (int)sm[3]));
            int sz = Math.max(1, (int)sm[2]);
            g2.setColor(new Color(160, 160, 160, alpha));
            g2.fillOval(muzzleX - sz/2 + (int)sm[0], muzzleY - sz/2 + (int)sm[1], sz, sz);
        }

        if (weaponRecoil > 0) weaponRecoil -= 3;
    }

    private void drawPowerWeapon(Graphics2D g2, int W, int H) {
        int bobX = (int)(Math.sin(bobPhase) * 5);
        int bobY = (int)(Math.abs(Math.sin(bobPhase)) * 6);
        int reloadOffset = 0;
        if (isReloading) {
            if (reloadProgress < 0.5) {
                reloadOffset = (int)(reloadProgress * 2 * 180);
            } else {
                reloadOffset = (int)((1.0 - reloadProgress) * 2 * 180);
            }
        }
        int baseY = H - 20 - weaponRecoil + bobY + reloadOffset;
        int cx    = W / 2 + 110 + bobX;

        // handle
        g2.setColor(new Color(160, 80, 10));
        g2.fillRoundRect(cx - 14, baseY - 80, 28, 75, 8, 8);

        // body
        g2.setColor(new Color(200, 120, 20));
        g2.fillRoundRect(cx - 30, baseY - 145, 60, 75, 6, 6);
        g2.setColor(new Color(240, 180, 60));
        g2.fillRect(cx - 24, baseY - 138, 22, 62);

        // large magazine
        g2.setColor(new Color(120, 60, 10));
        g2.fillRoundRect(cx - 10, baseY - 82, 20, 40, 4, 4);

        // heavy barrel
        g2.setColor(new Color(40, 20, 0));
        g2.fillRect(cx - 12, baseY - 210, 22, 70);
        g2.setColor(new Color(80, 40, 0));
        g2.fillRect(cx - 8, baseY - 207, 8, 64);

        // sight
        g2.setColor(new Color(40, 20, 0));
        g2.fillRect(cx - 18, baseY - 158, 34, 8);

        // orange glow
        g2.setColor(new Color(255, 140, 0, 30));
        g2.fillRoundRect(cx - 45, baseY - 220, 90, 220, 20, 20);

        if (weaponRecoil > 0) weaponRecoil -= 3;
    }

    private void drawBloodParticles(Graphics2D g2, double px, double py,
                                     double pa, int W, int H) {
        for (int[] p : new ArrayList<>(bloodParticles)) {
            double relX = p[0] - px;
            double relY = p[1] - py;
            double dist = Math.sqrt(relX*relX + relY*relY);
            if (dist < 1) continue;
            double diff = Math.atan2(relY, relX) - pa;
            while (diff >  Math.PI) diff -= 2*Math.PI;
            while (diff < -Math.PI) diff += 2*Math.PI;
            if (Math.abs(diff) > FOV/2.0 + 0.1) continue;
            int sx = (int)(W/2.0 + W * diff / FOV);
            int sy = H/2 - (int)(PROJ_SCALE * 0.05 / dist);
            int sz = Math.max(1, p[2]);
            g2.setColor(new Color(180, 0, 0, Math.min(255, p[3])));
            g2.fillOval(sx - sz/2, sy - sz/2, sz, sz);
        }
    }

    private void drawLampostLights(Graphics2D g2, double px, double py,
                                    double pa, int W, int H) {
        for (CityElement el : gameCityElements) {
            if (el.getType() != CityElement.Type.LAMPPOST) continue;
            double relX = el.getX() - px;
            double relY = el.getY() - py;
            double dist = Math.sqrt(relX*relX + relY*relY);
            if (dist > 400 || dist < 1) continue;
            double diff = Math.atan2(relY, relX) - pa;
            while (diff >  Math.PI) diff -= 2*Math.PI;
            while (diff < -Math.PI) diff += 2*Math.PI;
            if (Math.abs(diff) > FOV/2.0 + 0.3) continue;
            int sx = (int)(W/2.0 + W * diff / FOV);
            int radius = Math.max(20, (int)(300 / dist * 60));
            int alpha  = Math.max(10, (int)(80 - dist/5));
            g2.setColor(new Color(255, 220, 100, alpha));
            g2.fillOval(sx - radius, H/2 - radius/3, radius*2, radius*2/3);
        }
    }

    /** Returns {euclidean distance, texFrac 0..1 along the hit wall face}. */
    private double[] castRayFull(double px, double py, double rayAngle) {
        double dx  = Math.cos(rayAngle);
        double dy  = Math.sin(rayAngle);
        double minT    = Double.MAX_VALUE;
        double texFrac = 0.0;

        if (dx < -1e-6) {
            double t = -px / dx, hy = py + dy * t;
            if (t > 0 && inRange(hy, 0, WORLD_H) && t < minT) { minT = t; texFrac = hy / WORLD_H; }
        }
        if (dx >  1e-6) {
            double t = (WORLD_W - px) / dx, hy = py + dy * t;
            if (t > 0 && inRange(hy, 0, WORLD_H) && t < minT) { minT = t; texFrac = hy / WORLD_H; }
        }
        if (dy < -1e-6) {
            double t = -py / dy, hx = px + dx * t;
            if (t > 0 && inRange(hx, 0, WORLD_W) && t < minT) { minT = t; texFrac = hx / WORLD_W; }
        }
        if (dy >  1e-6) {
            double t = (WORLD_H - py) / dy, hx = px + dx * t;
            if (t > 0 && inRange(hx, 0, WORLD_W) && t < minT) { minT = t; texFrac = hx / WORLD_W; }
        }
        double dist = (minT == Double.MAX_VALUE) ? 1000.0 : minT;
        return new double[]{ dist, texFrac };
    }

    private static boolean inRange(double v, double lo, double hi) {
        return v >= lo && v <= hi;
    }

    private void drawEnemySprites(Graphics2D g2, double px, double py,
                                   double pa, int[] wallHeights) {
        int W = panel.getWidth();
        int H = panel.getHeight();
        List<Enemy> snapshot = new ArrayList<>(gameEnemies);
        snapshot.sort((a, b) -> Double.compare(b.distanceTo(px, py), a.distanceTo(px, py)));

        for (Enemy enemy : snapshot) {
            if (!enemy.isAlive()) continue;

            double relX = enemy.getX() - px;
            double relY = enemy.getY() - py;
            double dist = Math.sqrt(relX * relX + relY * relY);
            if (dist < 1) continue;

            double angleToEnemy = Math.atan2(relY, relX);
            double diff = angleToEnemy - pa;
            while (diff >  Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;

            if (Math.abs(diff) > FOV / 2.0 + 0.2) continue;

            int screenX    = (int)(W / 2.0 + W * diff / FOV);
            double perpDist = Math.max(1.0, dist * Math.cos(diff));
            int spriteH    = Math.min(H, (int)(PROJ_SCALE / perpDist));
            int spriteW    = spriteH;
            // feet are at 79% of sprite height (head17+body34+leg28=79%)
            // set spriteTop so feet land exactly on the floor line (H+spriteH)/2
            int floorY     = (H + spriteH) / 2;
            int spriteTop  = floorY - spriteH * 79 / 100;
            int spriteLeft = screenX - spriteW / 2;

            if (spriteH < 4) continue;

            boolean muzzle = (enemy.getId() == muzzleFlashEnemyId);
            drawSoldier(g2, screenX, spriteTop, spriteW, spriteH, dist, muzzle);

            int hpPct = enemyHealthPercent.getOrDefault(enemy.getId(), 100);
            int barY  = spriteTop - 8;
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(spriteLeft, barY, spriteW, 5);
            Color hpColor = hpPct > 50 ? new Color(50, 200, 80) :
                            hpPct > 25 ? new Color(220, 180, 0) : new Color(220, 50, 50);
            g2.setColor(hpColor);
            g2.fillRect(spriteLeft, barY, (int)(spriteW * hpPct / 100.0), 5);
        }

        // אויבים גוססים
        for (double[] d : new ArrayList<>(dyingEnemies)) {
            double relX = d[0] - px;
            double relY = d[1] - py;
            double dist = Math.sqrt(relX*relX + relY*relY);
            if (dist < 1) continue;
            double diff = Math.atan2(relY, relX) - pa;
            while (diff >  Math.PI) diff -= 2*Math.PI;
            while (diff < -Math.PI) diff += 2*Math.PI;
            if (Math.abs(diff) > FOV/2.0 + 0.2) continue;
            int sx = (int)(W/2.0 + W * diff / FOV);
            double perpDist = Math.max(1.0, dist * Math.cos(diff));
            int sh = Math.min(H, (int)(PROJ_SCALE / perpDist));
            int floorY = (H + sh) / 2;
            Graphics2D dg = (Graphics2D) g2.create();
            dg.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0f, (float)d[3] / 255f)));
            dg.rotate(Math.toRadians(d[2]), sx, floorY - sh/2);
            drawSoldier(dg, sx, floorY - sh * 79/100, sh, sh, dist, false);
            dg.dispose();
        }
    }

    private void drawSoldier(Graphics2D g2, int cx, int top,
                              int w, int h, double dist, boolean muzzle) {
        if (w < 6 || h < 6) return;
        float s = Math.max(0.25f, 1.0f - (float)(dist / 900.0));

        double walk = Math.sin(walkAnimMs / 110.0);
        int legOff = (int)(walk * Math.max(1, h / 14));

        // proportions
        int headH = Math.max(3, h * 15 / 100);
        int bodyH = Math.max(4, h * 35 / 100);
        int legH  = Math.max(3, h * 30 / 100);
        int bodyW = Math.max(5, w * 48 / 100);
        int headW = Math.max(3, w * 26 / 100);
        int legW  = Math.max(2, w * 14 / 100);
        int armW  = Math.max(2, w * 11 / 100);

        int headX = cx - headW / 2;
        int headY = top;
        int bodyX = cx - bodyW / 2;
        int bodyY = headY + headH;
        int legY  = bodyY + bodyH;

        // legs — two rects, walk offset
        g2.setColor(new Color((int)(50*s), (int)(70*s), (int)(40*s)));
        g2.fillRect(bodyX + 2,              legY + legOff,  legW, legH);
        g2.fillRect(bodyX + bodyW - legW - 2, legY - legOff, legW, legH);

        // body
        g2.setColor(new Color((int)(80*s), (int)(100*s), (int)(60*s)));
        g2.fillRect(bodyX, bodyY, bodyW, bodyH);

        // arms
        g2.setColor(new Color((int)(80*s), (int)(100*s), (int)(60*s)));
        g2.fillRect(bodyX - armW,     bodyY + (int)(walk * Math.max(1, h/20)), armW, bodyH * 7/10);
        g2.fillRect(bodyX + bodyW,    bodyY - (int)(walk * Math.max(1, h/20)), armW, bodyH * 7/10);

        // weapon — exits right side of body, aimed horizontally
        if (h > 18) {
            int thick  = Math.max(2, h / 18);
            int bodyGW = Math.max(4, w * 18 / 100);
            int barrel = Math.max(5, w * 28 / 100);
            int gunY   = bodyY + bodyH * 2 / 5;
            int gunX   = bodyX + bodyW;          // starts at right edge of body

            // גוף נשק — אפור
            g2.setColor(new Color(90, 90, 95));
            g2.fillRoundRect(gunX, gunY, bodyGW, thick * 2, 2, 2);

            // קנה — שחור, יוצא ימינה
            g2.setColor(new Color(20, 20, 20));
            g2.fillRect(gunX + bodyGW, gunY + thick / 2, barrel, thick);

            // מחסנית — חום, יורדת מהגוף
            g2.setColor(new Color(80, 55, 25));
            g2.fillRoundRect(gunX + bodyGW / 3, gunY + thick * 2,
                             thick + 1, thick * 3, 1, 1);

            // muzzle flash
            if (muzzle) {
                int tipX = gunX + bodyGW + barrel + 2;
                int tipY = gunY + thick;
                g2.setColor(new Color(255, 230, 80, 230));
                g2.fillOval(tipX - 4, tipY - 5, 12, 10);
                g2.setColor(new Color(255, 255, 200, 150));
                g2.fillOval(tipX - 7, tipY - 8, 18, 16);
            }
        }

        // head — skin
        g2.setColor(new Color((int)(180*s), (int)(140*s), (int)(100*s)));
        g2.fillOval(headX, headY + headH / 4, headW, headH * 3 / 4);
        // helmet — dark grey over top half
        g2.setColor(new Color((int)(60*s), (int)(60*s), (int)(60*s)));
        g2.fillArc(headX, headY, headW, headH, 0, 180);
    }

    private void drawCityElements(Graphics2D g2, double px, double py,
                                   double pa, int[] wallHeights) {
        int W = panel.getWidth();
        int H = panel.getHeight();

        List<CityElement> snapshot = new ArrayList<>(gameCityElements);
        snapshot.sort((a, b) -> Double.compare(
            Math.hypot(b.getX()-px, b.getY()-py),
            Math.hypot(a.getX()-px, a.getY()-py)));

        for (CityElement el : snapshot) {
            double relX = el.getX() - px;
            double relY = el.getY() - py;
            double dist = Math.sqrt(relX*relX + relY*relY);
            if (dist < 1) continue;

            double angle = Math.atan2(relY, relX);
            double diff  = angle - pa;
            while (diff >  Math.PI) diff -= 2*Math.PI;
            while (diff < -Math.PI) diff += 2*Math.PI;
            if (Math.abs(diff) > FOV/2.0 + 0.3) continue;

            int screenX = (int)(W/2.0 + W * diff / FOV);
            int baseH   = Math.min(H/2, (int)(PROJ_SCALE * 0.6 / dist));
            if (baseH < 5) continue;

            int midCol = Math.max(0, Math.min(W-1, screenX));
            if (wallHeights[midCol] >= H) continue;

            int groundY = H/2 + (int)(PROJ_SCALE * 0.5 / dist);

            switch (el.getType()) {
                case BUSH:      drawBush     (g2, screenX, groundY, baseH, dist); break;
                case TREE:      drawTree     (g2, screenX, groundY, baseH, dist); break;
                case LAMPPOST:  drawLamppost (g2, screenX, groundY, baseH, dist); break;
                case CAR:       drawCar      (g2, screenX, groundY, baseH, dist); break;
                case TRASH_CAN: drawTrashCan (g2, screenX, groundY, baseH, dist); break;
            }
        }
    }

    private void drawBush(Graphics2D g2, int cx, int groundY, int h, double dist) {
        int shade = Math.max(40, 200 - (int)(dist/3));
        int w = h * 2;
        g2.setColor(new Color(shade*28/100, shade*60/100, shade*22/100));
        g2.fillOval(cx - w/2,  groundY - h,      w*6/10, h);
        g2.setColor(new Color(shade*22/100, shade*52/100, shade*18/100));
        g2.fillOval(cx - w/4,  groundY - h*13/10, w*6/10, h);
        g2.setColor(new Color(shade*32/100, shade*65/100, shade*25/100));
        g2.fillOval(cx,        groundY - h*9/10,  w*5/10, h*9/10);
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillOval(cx - w/2,  groundY - 4, w, 8);
    }

    private void drawTree(Graphics2D g2, int cx, int groundY, int h, double dist) {
        int shade  = Math.max(40, 200 - (int)(dist/3));
        int trunkW = Math.max(3, h/6);
        int trunkH = h * 5/10;
        int crownW = h * 9/10;
        int crownH = h * 8/10;
        g2.setColor(new Color(shade*40/100, shade*28/100, shade*16/100));
        g2.fillRoundRect(cx - trunkW/2, groundY - trunkH, trunkW, trunkH, 3, 3);
        g2.setColor(new Color(shade*25/100, shade*55/100, shade*18/100));
        g2.fillOval(cx - crownW/2,   groundY - trunkH - crownH,       crownW,    crownH);
        g2.setColor(new Color(shade*30/100, shade*62/100, shade*22/100));
        g2.fillOval(cx - crownW*4/10, groundY - trunkH - crownH*13/10, crownW*8/10, crownH*8/10);
        g2.setColor(new Color(shade*35/100, shade*68/100, shade*26/100));
        g2.fillOval(cx - crownW*3/10, groundY - trunkH - crownH*16/10, crownW*6/10, crownH*7/10);
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fillOval(cx - crownW/2, groundY - 5, crownW, 10);
    }

    private void drawLamppost(Graphics2D g2, int cx, int groundY, int h, double dist) {
        int shade = Math.max(40, 200 - (int)(dist/3));
        int poleW = Math.max(2, h/14);
        g2.setColor(new Color(shade*50/100, shade*50/100, shade*55/100));
        g2.fillRect(cx - poleW/2, groundY - h, poleW, h);
        g2.fillRect(cx - poleW/2 - h/5, groundY - h, h/5, poleW);
        g2.setColor(new Color(255, 240, 180));
        g2.fillOval(cx - h/5 - h/10, groundY - h - h/8, h/5, h/8);
        g2.setColor(new Color(255, 240, 150, 80));
        g2.fillOval(cx - h/5 - h/6, groundY - h - h/5, h/3, h/3);
        g2.setColor(new Color(shade*40/100, shade*40/100, shade*44/100));
        g2.fillRect(cx - poleW, groundY - h/10, poleW*2, h/10);
    }

    private void drawCar(Graphics2D g2, int cx, int groundY, int h, double dist) {
        int shade = Math.max(40, 200 - (int)(dist/3));
        int carW  = h * 3;
        int carH  = h * 9/10;
        int roofW = carW * 6/10;
        int roofH = carH * 5/10;
        g2.setColor(new Color(shade*78/100, shade*22/100, shade*18/100));
        g2.fillRoundRect(cx - carW/2, groundY - carH, carW, carH, 6, 6);
        g2.setColor(new Color(shade*65/100, shade*18/100, shade*14/100));
        g2.fillRoundRect(cx - roofW/2, groundY - carH - roofH, roofW, roofH, 8, 8);
        g2.setColor(new Color(160, 210, 240, 200));
        g2.fillRoundRect(cx - roofW/2 + 4, groundY - carH - roofH + 4, roofW/2 - 6, roofH - 8, 4, 4);
        g2.fillRoundRect(cx + 2,            groundY - carH - roofH + 4, roofW/2 - 6, roofH - 8, 4, 4);
        g2.setColor(new Color(25, 25, 25));
        int wheelR = Math.max(3, h/4);
        g2.fillOval(cx - carW/2 + wheelR,   groundY - wheelR*2, wheelR*2, wheelR*2);
        g2.fillOval(cx + carW/2 - wheelR*3, groundY - wheelR*2, wheelR*2, wheelR*2);
        g2.setColor(new Color(180, 180, 180));
        g2.fillRect(cx - carW/2, groundY - carH + 4, carW, Math.max(1, carH/10));
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(cx - carW/2, groundY - 4, carW, 8);
    }

    private void drawTrashCan(Graphics2D g2, int cx, int groundY, int h, double dist) {
        int shade = Math.max(40, 200 - (int)(dist/3));
        int w = Math.max(4, h * 6/10);
        g2.setColor(new Color(shade*38/100, shade*42/100, shade*35/100));
        g2.fillRoundRect(cx - w/2, groundY - h, w, h, 4, 4);
        g2.setColor(new Color(shade*25/100, shade*28/100, shade*22/100));
        for (int i = 1; i <= 3; i++)
            g2.fillRect(cx - w/2 + 2, groundY - h + h*i/4, w - 4, Math.max(1, h/14));
        g2.setColor(new Color(shade*48/100, shade*52/100, shade*44/100));
        g2.fillRoundRect(cx - w/2 - 2, groundY - h - h/8, w + 4, h/8, 3, 3);
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(cx - w/2, groundY - 4, w, 7);
    }

    private void drawItemSprites(Graphics2D g2, double px, double py,
                                  double pa, int[] wallHeights, int[] floorYArr) {
        int W = panel.getWidth();
        int H = panel.getHeight();

        Map<Integer, double[]> posSnap  = new HashMap<>(itemPos);
        Map<Integer, String>   typeSnap = new HashMap<>(itemTypes);

        for (Map.Entry<Integer, double[]> entry : posSnap.entrySet()) {
            int      id   = entry.getKey();
            double[] pos  = entry.getValue();
            String   type = typeSnap.getOrDefault(id, "");

            double relX = pos[0] - px;
            double relY = pos[1] - py;
            double dist = Math.sqrt(relX * relX + relY * relY);
            if (dist < 1) continue;

            double angleToItem = Math.atan2(relY, relX);
            double diff = angleToItem - pa;
            while (diff >  Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;
            if (Math.abs(diff) > FOV / 2.0 + 0.2) continue;

            int screenX    = (int)(W / 2.0 + W * diff / FOV);
            double perpDist    = Math.max(1.0, dist * Math.cos(diff));
            int    wallHAtDist = Math.min(H, (int)(PROJ_SCALE / perpDist));
            int    spriteH     = Math.min(H / 5, wallHAtDist * 18 / 100);
            int    spriteW     = spriteH;
            // floor line matches the raycaster — same formula as wallBot
            int    floorY      = (H + wallHAtDist) / 2;
            int    spriteTop   = floorY - spriteH;
            int    spriteLeft  = screenX - spriteW / 2;

            boolean visible = false;
            for (int col = Math.max(0, spriteLeft); col < Math.min(W, spriteLeft + spriteW); col++) {
                if (wallHeights[col] < H) { visible = true; break; }
            }
            if (!visible || spriteH < 6) continue;

            boolean nearby = dist <= 60;
            boolean flash  = nearby && itemFlashState;

            // צל אליפטי על הרצפה
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillOval(spriteLeft - spriteW/4, floorY - 4,
                        spriteW + spriteW/2, 8);

            if ("Medkit".equals(type)) {
                drawMedkit(g2, screenX, spriteTop, spriteW, spriteH, flash);
            } else if ("PowerWeapon".equals(type)) {
                drawPowerWeaponItem(g2, screenX, spriteTop, spriteW, spriteH, flash);
            } else {
                drawAmmoBox(g2, screenX, spriteTop, spriteW, spriteH, flash);
            }

            int meters = (int)(dist / 10);
            g2.setFont(new Font("Arial", Font.BOLD, Math.max(9, spriteH / 4)));
            String distLabel = meters + "m";
            int lw = g2.getFontMetrics().stringWidth(distLabel);
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(screenX - lw/2 - 3, spriteTop - 14, lw + 6, 16, 4, 4);
            g2.setColor(nearby ? new Color(255, 220, 60) : Color.WHITE);
            g2.drawString(distLabel, screenX - lw/2, spriteTop);
        }
    }

    private void drawMedkit(Graphics2D g2, int cx, int top, int w, int h, boolean flash) {
        Color bg = flash ? new Color(255, 100, 100) : new Color(210, 35, 35);
        g2.setColor(bg);
        g2.fillRoundRect(cx - w/2, top, w, h, 8, 8);

        g2.setColor(new Color(255, 255, 255, 180));
        g2.setStroke(new BasicStroke(Math.max(1.5f, w/12f)));
        g2.drawRoundRect(cx - w/2, top, w, h, 8, 8);

        int barW = Math.max(3, w / 3);
        int pad  = Math.max(3, h / 5);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(cx - barW/2,     top + pad,          barW,      h - pad*2, 3, 3);
        g2.fillRoundRect(cx - w/2 + pad,  top + h/2 - barW/2, w - pad*2, barW,      3, 3);

        g2.setColor(new Color(255, 255, 255, 80));
        g2.fillOval(cx - w/2 + 3, top + 3, w/4, h/5);
    }

    private void drawAmmoBox(Graphics2D g2, int cx, int top, int w, int h, boolean flash) {
        Color bg = flash ? new Color(255, 210, 60) : new Color(190, 140, 10);
        g2.setColor(bg);
        g2.fillRoundRect(cx - w/2, top, w, h, 5, 5);

        g2.setColor(new Color(100, 65, 0));
        g2.setStroke(new BasicStroke(Math.max(1.5f, w/12f)));
        g2.drawRoundRect(cx - w/2, top, w, h, 5, 5);

        int bulletW   = Math.max(2, w/6);
        int bulletH   = Math.max(4, h/2);
        int spacing   = w / 4;
        for (int i = 0; i < 3; i++) {
            int bx = cx - spacing + i * spacing - bulletW/2;
            int by = top + h/2 - bulletH/2;
            g2.setColor(new Color(220, 190, 50));
            g2.fillRoundRect(bx, by, bulletW, bulletH, 2, 2);
            g2.setColor(new Color(180, 140, 30));
            g2.fillRoundRect(bx, by, bulletW, bulletH/3, 2, 2);
        }

        g2.setColor(new Color(255, 255, 255, 70));
        g2.fillOval(cx - w/2 + 3, top + 3, w/4, h/5);
    }

    private void drawPowerWeaponItem(Graphics2D g2, int cx, int top,
                                     int w, int h, boolean flash) {
        Color bg = flash ? new Color(255, 220, 80) : new Color(220, 140, 0);
        g2.setColor(bg);
        g2.fillRoundRect(cx - w/2, top, w, h, 6, 6);

        g2.setColor(new Color(160, 90, 0));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(cx - w/2, top, w, h, 6, 6);

        g2.setFont(new Font("Arial", Font.BOLD, Math.max(10, h * 2/3)));
        g2.setColor(Color.WHITE);
        String label = "!";
        int lw = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, cx - lw/2, top + h * 3/4);

        g2.setColor(new Color(255, 200, 0, 60));
        g2.fillRoundRect(cx - w/2 - 4, top - 4, w + 8, h + 8, 10, 10);
    }

    // ── Phase B: HUD overlay ─────────────────────────────────────────────────

    private void renderHUD(Graphics2D g2) {
        int W = panel.getWidth();
        int H = panel.getHeight();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ── פס שחור למעלה ──
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, W, 45);

        // SCORE
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.setColor(new Color(255, 215, 0));
        String scoreStr = "SCORE  " + score;
        int sw = g2.getFontMetrics().stringWidth(scoreStr);
        g2.drawString(scoreStr, W/2 - sw/2, 28);

        // WAVE
        if (currentWave > 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.setColor(new Color(180, 130, 255));
            String waveStr = "WAVE " + currentWave + " / " + WaveManager.TOTAL_WAVES;
            int ww = g2.getFontMetrics().stringWidth(waveStr);
            g2.drawString(waveStr, W/2 - ww/2, 42);
        }

        // ── HP — פינה ימין למטה ──
        int hpBarW = 180;
        int hpBarH = 22;
        int hpX    = W - hpBarW - 20;
        int hpY    = H - 55;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(hpX - 10, hpY - 20, hpBarW + 20, hpBarH + 28, 10, 10);

        g2.setFont(new Font("Arial", Font.BOLD, 13));
        g2.setColor(new Color(200, 200, 200));
        g2.drawString("HP", hpX, hpY - 4);

        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(hpX, hpY, hpBarW, hpBarH, 6, 6);

        Color hpColor = health > 50 ? new Color(50, 200, 80) :
                        health > 25 ? new Color(220, 180, 0) : new Color(220, 50, 50);
        g2.setColor(hpColor);
        g2.fillRoundRect(hpX, hpY, (int)(hpBarW * health / 100.0), hpBarH, 6, 6);

        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString(health + " / 100", hpX + hpBarW / 2 - 25, hpY + 16);

        // ── AMMO — פינה שמאל למטה ──
        int ammoX = 20;
        int ammoY = H - 55;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(ammoX - 10, ammoY - 20, 160, hpBarH + 28, 10, 10);

        g2.setColor(new Color(220, 180, 60));
        g2.fillRoundRect(ammoX, ammoY - 2, 8, 18, 3, 3);
        g2.setColor(new Color(180, 140, 40));
        g2.fillRect(ammoX, ammoY + 10, 8, 6);

        boolean reloading = gamePlayer != null && gamePlayer.isReloading();
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        if (reloading) {
            g2.setColor(new Color(255, 165, 0));
            g2.drawString("--", ammoX + 16, ammoY + 18);
        } else {
            g2.setColor(Color.WHITE);
            g2.drawString(ammo + "", ammoX + 16, ammoY + 18);
        }
        g2.setFont(new Font("Arial", Font.BOLD, 13));
        g2.setColor(new Color(160, 160, 160));
        g2.drawString("/ " + Player.MAX_AMMO, ammoX + 50, ammoY + 18);

        g2.setFont(new Font("Arial", Font.BOLD, 13));
        g2.setColor(new Color(200, 200, 200));
        g2.drawString("AMMO", ammoX, ammoY - 4);

        if (reloading) {
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.setColor(new Color(255, 165, 0));
            g2.drawString("RELOADING...", ammoX, ammoY - 28);
        }

        // ── COVER אינדיקטור ──
        if (inCover) {
            g2.setFont(new Font("Arial", Font.BOLD, 15));
            g2.setColor(new Color(220, 60, 60));
            g2.drawString("▼ COVER", 20, panel.getHeight() - 80);
        }

        // ── POWER WEAPON אינדיקטור ──
        if (weaponPowered) {
            g2.setFont(new Font("Arial", Font.BOLD, 15));
            g2.setColor(new Color(255, 160, 0));
            g2.drawString("⚡ POWER WEAPON", 20, H - 100);
        }

        // ── Pickup prompt ──
        if (!nearbyItemName.isEmpty()) {
            String prompt = "Press E — " + nearbyItemName;
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            int pw  = g2.getFontMetrics().stringWidth(prompt);
            int px2 = (W - pw) / 2;
            int py2 = H * 3 / 4;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(px2 - 10, py2 - 22, pw + 20, 30, 8, 8);
            g2.setColor(new Color(255, 220, 60));
            g2.drawString(prompt, px2, py2);
        }

        // ── Crosshair ──
        g2.setColor(new Color(255, 255, 255, 180));
        g2.setStroke(new BasicStroke(1.5f));
        int cx = W/2, cy = H/2;
        g2.drawLine(cx - 10, cy, cx - 4, cy);
        g2.drawLine(cx + 4,  cy, cx + 10, cy);
        g2.drawLine(cx, cy - 10, cx, cy - 4);
        g2.drawLine(cx, cy + 4,  cx, cy + 10);

        // ── INCOMING ──
        if (showIncoming) {
            g2.setFont(new Font("Arial", Font.BOLD, 28));
            int iw = g2.getFontMetrics().stringWidth("!! INCOMING !!");
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(W/2 - iw/2 - 12, H/2 + 60, iw + 24, 38, 8, 8);
            g2.setColor(new Color(255, 60, 60));
            g2.drawString("!! INCOMING !!", W/2 - iw/2, H/2 + 88);
        }

        // ── Killstreak ──
        if (!killStreakMsg.isEmpty()) {
            Color ksColor = killStreakCount >= 5 ? new Color(255, 80,  80)  :
                            killStreakCount >= 3 ? new Color(255, 160, 0)   :
                                                  new Color(255, 215, 0);
            g2.setFont(new Font("Arial", Font.BOLD, 32));
            int kw = g2.getFontMetrics().stringWidth(killStreakMsg);
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(W/2 - kw/2 - 16, H/2 - 80, kw + 32, 44, 10, 10);
            g2.setColor(ksColor);
            g2.drawString(killStreakMsg, W/2 - kw/2, H/2 - 46);
        }

        // ── Wave incoming overlay ──
        if (showWaveScreen) {
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRect(0, 0, W, H);
            drawCentered(g2, "WAVE " + incomingWave + " INCOMING",
                new Font("Arial", Font.BOLD, 52), new Color(255, 80, 80), H/2 - 40);
            drawCentered(g2, "Starting in " + waveCountdown + "...",
                new Font("Arial", Font.BOLD, 28), Color.WHITE, H/2 + 20);
        }

        // ── Timed message ──
        if (!message.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            int mw = g2.getFontMetrics().stringWidth(message);
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(W/2 - mw/2 - 10, H - 80, mw + 20, 28, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(message, W/2 - mw/2, H - 62);
        }

        drawMinimap(g2, W, H);
    }

    private void drawMinimap(Graphics2D g2, int W, int H) {
        if (gamePlayer == null) return;

        final int    MAP_SIZE = 160;
        final int    MAP_X    = W - MAP_SIZE - 20;
        final int    MAP_Y    = 55;
        final double SCALE    = MAP_SIZE / 1280.0;
        final double SCALE_Y  = MAP_SIZE / 720.0;

        // רקע
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(MAP_X - 4, MAP_Y - 4, MAP_SIZE + 8, MAP_SIZE + 8, 8, 8);
        g2.setColor(new Color(60, 60, 60, 200));
        g2.fillRoundRect(MAP_X, MAP_Y, (int)(1280 * SCALE), (int)(720 * SCALE_Y), 4, 4);

        // מסגרת
        g2.setColor(new Color(120, 120, 120, 200));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(MAP_X, MAP_Y, (int)(1280 * SCALE), (int)(720 * SCALE_Y), 4, 4);

        // אלמנטי עיר
        for (CityElement el : gameCityElements) {
            int ex = MAP_X + (int)(el.getX() * SCALE);
            int ey = MAP_Y + (int)(el.getY() * SCALE_Y);
            Color elColor;
            switch (el.getType()) {
                case TREE:
                case BUSH:     elColor = new Color(40, 120, 40, 180);  break;
                case CAR:      elColor = new Color(180, 60, 60, 180);  break;
                case LAMPPOST: elColor = new Color(220, 200, 80, 180); break;
                default:       elColor = new Color(100, 100, 100, 180); break;
            }
            g2.setColor(elColor);
            g2.fillOval(ex - 2, ey - 2, 4, 4);
        }

        // פריטים
        for (Map.Entry<Integer, double[]> entry : itemPos.entrySet()) {
            double[] pos = entry.getValue();
            String   type = itemTypes.getOrDefault(entry.getKey(), "");
            int ix = MAP_X + (int)(pos[0] * SCALE);
            int iy = MAP_Y + (int)(pos[1] * SCALE_Y);
            g2.setColor("Medkit".equals(type) ? new Color(50, 220, 80) : new Color(255, 180, 0));
            g2.fillOval(ix - 3, iy - 3, 6, 6);
        }

        // אויבים
        for (Enemy enemy : new ArrayList<>(gameEnemies)) {
            if (!enemy.isAlive()) continue;
            int ex = MAP_X + (int)(enemy.getX() * SCALE);
            int ey = MAP_Y + (int)(enemy.getY() * SCALE_Y);
            g2.setColor(new Color(220, 50, 50));
            g2.fillOval(ex - 4, ey - 4, 8, 8);
            g2.setColor(new Color(140, 0, 0));
            g2.fillOval(ex - 2, ey - 2, 4, 4);
        }

        // שחקן
        double px     = gamePlayer.getX();
        double py     = gamePlayer.getY();
        double pa     = gamePlayer.getAngle();
        int    ppx    = MAP_X + (int)(px * SCALE);
        int    ppy    = MAP_Y + (int)(py * SCALE_Y);
        double fovH   = FOV / 2.0;
        int    fovLen = 20;

        // חרוט שדה ראייה
        int fov1x = ppx + (int)(Math.cos(pa - fovH) * fovLen);
        int fov1y = ppy + (int)(Math.sin(pa - fovH) * fovLen);
        int fov2x = ppx + (int)(Math.cos(pa + fovH) * fovLen);
        int fov2y = ppy + (int)(Math.sin(pa + fovH) * fovLen);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillPolygon(new int[]{ppx, fov1x, fov2x}, new int[]{ppy, fov1y, fov2y}, 3);

        // נקודת שחקן
        g2.setColor(Color.WHITE);
        g2.fillOval(ppx - 5, ppy - 5, 10, 10);
        g2.setColor(new Color(100, 180, 255));
        g2.fillOval(ppx - 3, ppy - 3, 6, 6);

        // כיוון מבט
        int arrowX = ppx + (int)(Math.cos(pa) * 12);
        int arrowY = ppy + (int)(Math.sin(pa) * 12);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(ppx, ppy, arrowX, arrowY);

        // תווית
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        g2.setColor(new Color(200, 200, 200));
        g2.drawString("MAP", MAP_X + 4, MAP_Y - 6);
    }
}
