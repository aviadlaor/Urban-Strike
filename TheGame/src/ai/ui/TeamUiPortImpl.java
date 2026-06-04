package ai.ui;

import ai.model.Enemy;
import ai.model.Player;
import shared.ui_ports.TeamUiPort;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamUiPortImpl extends TeamUiPort {

    private static final int    SCREEN_W   = 800;
    private static final int    SCREEN_H   = 600;
    private static final double FOV        = Math.PI / 3;    // 60°
    private static final double WORLD_W    = 800.0;
    private static final double WORLD_H    = 600.0;
    // Projection constant: wallHeight = PROJ_SCALE / perpDist.
    // Tuned so walls at ~300 units fill ~44% of screen height.
    private static final double PROJ_SCALE = 80_000.0;

    // ── Push-based state from backend ────────────────────────────────────────
    private double  playerX, playerY;
    private Map<Integer, double[]> enemies   = new HashMap<>();
    private Map<Integer, double[]> itemPos   = new HashMap<>(); // id → {x, y}
    private Map<Integer, String>   itemTypes = new HashMap<>(); // id → "Medkit"|"AmmoBox"
    private int     health        = 100;
    private int     score         = 0;
    private int     ammo          = 30;
    private String  message       = "";
    private boolean showFireFlash = false;

    // ── Direct model references set once at startGame ────────────────────────
    private final JPanel    panel;
    private Player          gamePlayer;
    private List<Enemy>     gameEnemies = new ArrayList<>();

    public TeamUiPortImpl(JPanel panel) { this.panel = panel; }

    public void setGameWorld(Player player, List<Enemy> enemies) {
        this.gamePlayer  = player;
        this.gameEnemies = enemies;
    }

    // ── TeamUiPort interface ─────────────────────────────────────────────────

    @Override public void method1(int id) {}

    @Override public void showPlayer(double x, double y)  { playerX = x; playerY = y; panel.repaint(); }
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
        showFireFlash = true; panel.repaint();
        Timer t = new Timer(80, e -> { showFireFlash = false; panel.repaint(); });
        t.setRepeats(false); t.start();
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

    // ── Entry point called by GamePanel.paintComponent ───────────────────────

    public void render(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (gamePlayer == null) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, SCREEN_W, SCREEN_H);
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("Arial", Font.PLAIN, 18));
            g2.drawString("Waiting for game state...", 20, 50);
            return;
        }

        renderWorld(g2);   // Phase A: 3D raycasted scene
        renderHUD(g2);     // Phase B: HUD overlay — always on top
    }

    // ── Phase A: FPS raycasted world ─────────────────────────────────────────

    private void renderWorld(Graphics2D g2) {
        double px = gamePlayer.getX();
        double py = gamePlayer.getY();
        double pa = gamePlayer.getAngle();

        // Ceiling
        g2.setColor(new Color(26, 26, 26));
        g2.fillRect(0, 0, SCREEN_W, SCREEN_H / 2);

        // Floor
        g2.setColor(new Color(42, 42, 42));
        g2.fillRect(0, SCREEN_H / 2, SCREEN_W, SCREEN_H / 2);

        // Cast one ray per screen column, record wall heights for sprite z-test
        int[] wallHeights = new int[SCREEN_W];

        for (int col = 0; col < SCREEN_W; col++) {
            double rayAngle = pa - FOV / 2.0 + (col / (double) SCREEN_W) * FOV;
            double dist     = castRay(px, py, rayAngle);

            // Fish-eye correction: perpendicular distance flattens the spherical lens warp
            double perpDist = Math.max(1.0, dist * Math.cos(rayAngle - pa));

            int wallH   = Math.min(SCREEN_H, (int)(PROJ_SCALE / perpDist));
            wallHeights[col] = wallH;
            int wallTop = (SCREEN_H - wallH) / 2;

            // Distance shading — blue-grey tint, darker when far
            int shade = Math.max(30, 220 - (int)(perpDist / 3.0));
            g2.setColor(new Color(shade / 2, shade / 2, shade));
            g2.drawLine(col, wallTop, col, wallTop + wallH);
        }

        drawEnemySprites(g2, px, py, pa, wallHeights);
        drawItemSprites(g2, px, py, pa, wallHeights);

        // Fire flash: bright crosshair flare instead of a 2D ray line
        if (showFireFlash) {
            g2.setColor(new Color(255, 200, 0, 200));
            g2.setStroke(new BasicStroke(3f));
            int cx = SCREEN_W / 2, cy = SCREEN_H / 2;
            g2.drawLine(cx - 16, cy, cx + 16, cy);
            g2.drawLine(cx, cy - 16, cx, cy + 16);
        }
    }

    /**
     * Parametric ray–boundary intersection against the 4 world walls.
     * Returns the Euclidean distance to the nearest hit in world-pixel units.
     */
    private double castRay(double px, double py, double rayAngle) {
        double dx  = Math.cos(rayAngle);
        double dy  = Math.sin(rayAngle);
        double min = Double.MAX_VALUE;

        if (dx < -1e-6) {                                    // left  wall  x=0
            double t = -px / dx;
            if (t > 0 && inRange(py + dy * t, 0, WORLD_H)) min = Math.min(min, t);
        }
        if (dx >  1e-6) {                                    // right wall  x=WORLD_W
            double t = (WORLD_W - px) / dx;
            if (t > 0 && inRange(py + dy * t, 0, WORLD_H)) min = Math.min(min, t);
        }
        if (dy < -1e-6) {                                    // top   wall  y=0
            double t = -py / dy;
            if (t > 0 && inRange(px + dx * t, 0, WORLD_W)) min = Math.min(min, t);
        }
        if (dy >  1e-6) {                                    // bottom wall y=WORLD_H
            double t = (WORLD_H - py) / dy;
            if (t > 0 && inRange(px + dx * t, 0, WORLD_W)) min = Math.min(min, t);
        }

        return min == Double.MAX_VALUE ? 1000.0 : min;
    }

    private static boolean inRange(double v, double lo, double hi) {
        return v >= lo && v <= hi;
    }

    private void drawEnemySprites(Graphics2D g2, double px, double py,
                                   double pa, int[] wallHeights) {
        // Snapshot to avoid ConcurrentModificationException with the AI thread
        List<Enemy> snapshot = new ArrayList<>(gameEnemies);

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

            if (Math.abs(diff) > FOV / 2.0 + 0.2) continue;  // outside view frustum

            int screenX   = (int)(SCREEN_W / 2.0 + SCREEN_W * diff / FOV);
            int spriteH   = Math.min(SCREEN_H, (int)(PROJ_SCALE / dist));
            int spriteW   = spriteH;
            int spriteTop = (SCREEN_H - spriteH) / 2;
            int spriteLeft = screenX - spriteW / 2;

            // Z-buffer: only render where the sprite is closer than the wall behind it
            boolean inFront = false;
            for (int col = Math.max(0, spriteLeft); col < Math.min(SCREEN_W, spriteLeft + spriteW); col++) {
                if (wallHeights[col] <= spriteH) { inFront = true; break; }
            }
            if (!inFront) continue;

            int shade = Math.max(40, 200 - (int)(dist / 3.0));
            g2.setColor(new Color(shade, shade / 5, shade / 5));
            g2.fillRect(spriteLeft, spriteTop, spriteW, spriteH);
            g2.setColor(new Color(255, 80, 80));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(spriteLeft, spriteTop, spriteW, spriteH);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.drawString(String.valueOf(enemy.getId()), screenX - 4, spriteTop + spriteH + 13);
        }
    }

    private void drawItemSprites(Graphics2D g2, double px, double py,
                                  double pa, int[] wallHeights) {
        // Snapshot both maps together to stay consistent
        Map<Integer, double[]> posSnap  = new HashMap<>(itemPos);
        Map<Integer, String>   typeSnap = new HashMap<>(itemTypes);

        for (Map.Entry<Integer, double[]> entry : posSnap.entrySet()) {
            int    id   = entry.getKey();
            double[] pos = entry.getValue();
            String type  = typeSnap.getOrDefault(id, "");

            double relX = pos[0] - px;
            double relY = pos[1] - py;
            double dist = Math.sqrt(relX * relX + relY * relY);
            if (dist < 1) continue;

            double angleToItem = Math.atan2(relY, relX);
            double diff = angleToItem - pa;
            while (diff >  Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;

            if (Math.abs(diff) > FOV / 2.0 + 0.2) continue;

            int screenX   = (int)(SCREEN_W / 2.0 + SCREEN_W * diff / FOV);
            int spriteH   = Math.min(SCREEN_H / 4, (int)(PROJ_SCALE * 0.35 / dist));
            int spriteW   = spriteH;
            int spriteTop = SCREEN_H / 2;          // sit on the floor (horizon line)
            int spriteLeft = screenX - spriteW / 2;

            // Z-buffer: item is visible only where the wall is farther
            boolean visible = false;
            for (int col = Math.max(0, spriteLeft); col < Math.min(SCREEN_W, spriteLeft + spriteW); col++) {
                if (wallHeights[col] < SCREEN_H) { visible = true; break; }
            }
            if (!visible) continue;

            Color c = "Medkit".equals(type) ? new Color(50, 200, 80) : new Color(255, 200, 0);
            g2.setColor(c);
            g2.fillRect(spriteLeft, spriteTop, spriteW, spriteH);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(spriteLeft, spriteTop, spriteW, spriteH);
            g2.setFont(new Font("Arial", Font.BOLD, 9));
            g2.drawString("Medkit".equals(type) ? "M" : "A", screenX - 3, spriteTop + spriteH / 2 + 4);
        }
    }

    // ── Phase B: HUD overlay — always rendered last, always on top ───────────

    private void renderHUD(Graphics2D g2) {
        // Semi-transparent top bar
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, SCREEN_W, 36);

        g2.setFont(new Font("Arial", Font.BOLD, 15));

        // Health bar track + fill
        g2.setColor(Color.GRAY);
        g2.fillRoundRect(10, 8, 120, 18, 6, 6);
        Color hpColor = health > 50 ? new Color(50, 200, 80) :
                        health > 25 ? new Color(220, 180, 0) : new Color(220, 50, 50);
        g2.setColor(hpColor);
        g2.fillRoundRect(10, 8, (int)(120 * health / 100.0), 18, 6, 6);

        g2.setColor(Color.WHITE);
        g2.drawString("HP " + health, 16, 22);

        // Ammo display — orange and "--" while reloading
        boolean reloading = gamePlayer != null && gamePlayer.isReloading();
        if (reloading) {
            g2.setColor(new Color(255, 165, 0));
            g2.drawString("AMO -- / " + Player.MAX_AMMO, 145, 22);
        } else {
            g2.setColor(Color.WHITE);
            g2.drawString("AMO " + ammo + " / " + Player.MAX_AMMO, 145, 22);
        }

        g2.setColor(new Color(255, 215, 0));
        g2.drawString("SCORE " + score, SCREEN_W - 130, 22);

        // RELOADING... center overlay
        if (reloading) {
            g2.setFont(new Font("Arial", Font.BOLD, 20));
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(SCREEN_W / 2 - 105, SCREEN_H / 2 + 28, 210, 36, 8, 8);
            g2.setColor(new Color(255, 165, 0));
            g2.drawString("RELOADING...", SCREEN_W / 2 - 88, SCREEN_H / 2 + 52);
        }

        // Crosshair
        g2.setColor(new Color(255, 255, 255, 160));
        g2.setStroke(new BasicStroke(1.5f));
        int cx = SCREEN_W / 2, cy = SCREEN_H / 2;
        g2.drawLine(cx - 8, cy, cx - 3, cy);
        g2.drawLine(cx + 3, cy, cx + 8, cy);
        g2.drawLine(cx, cy - 8, cx, cy - 3);
        g2.drawLine(cx, cy + 3, cx, cy + 8);

        // Timed message (e.g. "Game Over")
        if (!message.isEmpty()) {
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(SCREEN_W / 2 - 130, SCREEN_H - 60, 260, 30, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.drawString(message, SCREEN_W / 2 - 120, SCREEN_H - 40);
        }
    }
}
