package ai.ui;

import ai.model.Enemy;
import ai.model.Player;
import java.awt.*;
import java.util.List;

public class Raycaster {

    private static final double FOV = Math.PI / 3;
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;
    private static final int WALL_COLOR = 0xFF404050;
    private static final int CEILING_COLOR = 0xFF1a1a1a;
    private static final int FLOOR_COLOR = 0xFF2a2a2a;
    private static final int ENEMY_COLOR = 0xFFC82828;
    private static final double WORLD_MIN_X = 0;
    private static final double WORLD_MAX_X = 800;
    private static final double WORLD_MIN_Y = 0;
    private static final double WORLD_MAX_Y = 600;
    private static final double MIN_DISTANCE = 1.0;

    public static void render(Graphics2D g2, Player player, List<Enemy> enemies,
                              int health, int ammo, int score) {
        double playerX = player.getX();
        double playerY = player.getY();
        double playerAngle = player.getAngle();

        int[] wallHeights = new int[SCREEN_WIDTH];

        g2.setColor(new Color(CEILING_COLOR));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT / 2);

        g2.setColor(new Color(FLOOR_COLOR));
        g2.fillRect(0, SCREEN_HEIGHT / 2, SCREEN_WIDTH, SCREEN_HEIGHT / 2);

        for (int col = 0; col < SCREEN_WIDTH; col++) {
            double rayAngle = playerAngle - FOV / 2.0 + (col / (double) SCREEN_WIDTH) * FOV;
            double distance = castRay(playerX, playerY, rayAngle);
            distance = Math.max(distance, MIN_DISTANCE);

            int wallHeight = (int) (SCREEN_HEIGHT * 0.5 / (Math.log(distance + 1) * 0.5 + 0.1));
            wallHeight = Math.min(wallHeight, SCREEN_HEIGHT);

            int wallStartY = (SCREEN_HEIGHT - wallHeight) / 2;
            wallHeights[col] = wallHeight;

            g2.setColor(new Color(WALL_COLOR));
            g2.drawLine(col, wallStartY, col, wallStartY + wallHeight);
        }

        drawEnemySprites(g2, enemies, playerX, playerY, playerAngle, wallHeights);

        drawHUD(g2, health, ammo, score);
    }

    private static double castRay(double playerX, double playerY, double rayAngle) {
        double dx = Math.cos(rayAngle);
        double dy = Math.sin(rayAngle);

        double minDistance = Double.MAX_VALUE;

        if (Math.abs(dx) > 1e-6) {
            double tLeft = -playerX / dx;
            if (tLeft > 0) {
                double hitY = playerY + dy * tLeft;
                if (hitY >= WORLD_MIN_Y && hitY <= WORLD_MAX_Y) {
                    minDistance = Math.min(minDistance, tLeft);
                }
            }

            double tRight = (WORLD_MAX_X - playerX) / dx;
            if (tRight > 0) {
                double hitY = playerY + dy * tRight;
                if (hitY >= WORLD_MIN_Y && hitY <= WORLD_MAX_Y) {
                    minDistance = Math.min(minDistance, tRight);
                }
            }
        }

        if (Math.abs(dy) > 1e-6) {
            double tTop = -playerY / dy;
            if (tTop > 0) {
                double hitX = playerX + dx * tTop;
                if (hitX >= WORLD_MIN_X && hitX <= WORLD_MAX_X) {
                    minDistance = Math.min(minDistance, tTop);
                }
            }

            double tBottom = (WORLD_MAX_Y - playerY) / dy;
            if (tBottom > 0) {
                double hitX = playerX + dx * tBottom;
                if (hitX >= WORLD_MIN_X && hitX <= WORLD_MAX_X) {
                    minDistance = Math.min(minDistance, tBottom);
                }
            }
        }

        return minDistance == Double.MAX_VALUE ? 100 : minDistance;
    }

    private static void drawEnemySprites(Graphics2D g2, List<Enemy> enemies,
                                         double playerX, double playerY,
                                         double playerAngle, int[] wallHeights) {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;

            double relX = enemy.getX() - playerX;
            double relY = enemy.getY() - playerY;
            double distToEnemy = Math.sqrt(relX * relX + relY * relY);

            if (distToEnemy < 1) continue;

            double angleToEnemy = Math.atan2(relY, relX);
            double angleDiff = angleToEnemy - playerAngle;

            while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
            while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

            if (Math.abs(angleDiff) > FOV / 2.0 + 0.3) continue;

            int screenX = (int) (SCREEN_WIDTH / 2.0 + SCREEN_WIDTH * angleDiff / FOV);
            int enemySize = (int) Math.max(10, 100.0 / Math.sqrt(distToEnemy));
            int screenY = SCREEN_HEIGHT / 2 - enemySize / 2;

            g2.setColor(new Color(ENEMY_COLOR));
            g2.fillRect(screenX - enemySize / 2, screenY, enemySize, enemySize);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(screenX - enemySize / 2, screenY, enemySize, enemySize);

            int distText = (int) distToEnemy;
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.drawString(String.valueOf(enemy.getId()), screenX - 5, screenY + enemySize + 12);
        }
    }

    private static void drawHUD(Graphics2D g2, int health, int ammo, int score) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, SCREEN_WIDTH, 36);

        g2.setFont(new Font("Arial", Font.BOLD, 15));
        g2.setColor(Color.GRAY);
        g2.fillRoundRect(10, 8, 120, 18, 6, 6);

        Color hpColor = health > 50 ? new Color(50, 200, 80) :
                        health > 25 ? new Color(220, 180, 0) : new Color(220, 50, 50);
        g2.setColor(hpColor);
        g2.fillRoundRect(10, 8, (int) (120 * health / 100.0), 18, 6, 6);

        g2.setColor(Color.WHITE);
        g2.drawString("HP " + health, 16, 22);
        g2.drawString("AMO " + ammo, 145, 22);

        g2.setColor(new Color(255, 215, 0));
        g2.drawString("SCORE " + score, SCREEN_WIDTH - 130, 22);
    }
}
