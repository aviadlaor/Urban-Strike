package ai.ui;

import base.Params;
import my_base.App;
import shared.MainRouter;
import shared.routers.ItemRouter;
import shared.routers.WeaponRouter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class GamePanel extends JPanel implements KeyListener {

    private final MainRouter   mainRouter;
    private final WeaponRouter weaponRouter;
    private final ItemRouter   itemRouter;
    private TeamUiPortImpl uiPort;
    private boolean isGameOver      = false;
    private boolean showTitleScreen = true;

    private java.awt.Robot robot;
    private boolean mouseWarping = false;
    private static final double MOUSE_SENSITIVITY = 0.003;

    public GamePanel(MainRouter mainRouter, WeaponRouter weaponRouter, ItemRouter itemRouter) {
        this.mainRouter   = mainRouter;
        this.weaponRouter = weaponRouter;
        this.itemRouter   = itemRouter;
        setBackground(Color.BLACK);
        setFocusable(true);

        // Hidden cursor
        BufferedImage cursorImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            cursorImg, new java.awt.Point(0, 0), "blank");
        setCursor(blankCursor);

        addKeyListener(this);

        try { robot = new java.awt.Robot(); } catch (Exception ex) { ex.printStackTrace(); }

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) { handleMouseDelta(e); }
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) { handleMouseDelta(e); }
        });

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    weaponRouter.route("/fireWeapon", Params.of());
                }
            }
        });
    }

    private void handleMouseDelta(java.awt.event.MouseEvent e) {
        if (robot == null) return;
        if (mouseWarping) return;

        int centerX = getWidth()  / 2;
        int centerY = getHeight() / 2;

        int deltaX = e.getX() - centerX;
        if (deltaX == 0) return;

        double newAngle = App.content().urbanStrikeBackend()
                             .getWorld().getPlayer().getAngle()
                          + deltaX * MOUSE_SENSITIVITY;

        mainRouter.route("/team/setAngle", Params.of(newAngle));

        mouseWarping = true;
        java.awt.Point screenCenter = new java.awt.Point(centerX, centerY);
        SwingUtilities.convertPointToScreen(screenCenter, GamePanel.this);
        robot.mouseMove(screenCenter.x, screenCenter.y);
        mouseWarping = false;
    }

    public void setUiPort(TeamUiPortImpl uiPort) { this.uiPort = uiPort; }
    public void setGameOver(boolean v)            { this.isGameOver = v; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (uiPort != null) {
            try {
                uiPort.render(g);
            } catch (Exception ex) {
                ex.printStackTrace();
                g.setColor(Color.RED);
                g.drawString("Render error: " + ex.getMessage(), 20, 50);
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                if (showTitleScreen) {
                    showTitleScreen = false;
                    uiPort.hideTitleScreen();
                    App.content().urbanStrikeBackend().startGame();
                }
                break;
            case KeyEvent.VK_W: case KeyEvent.VK_UP:
                mainRouter.route("/team/movePlayer", Params.of("UP")); break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN:
                mainRouter.route("/team/movePlayer", Params.of("DOWN")); break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT:
                mainRouter.route("/team/movePlayer", Params.of("LEFT")); break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT:
                mainRouter.route("/team/movePlayer", Params.of("RIGHT")); break;
            case KeyEvent.VK_SPACE: case KeyEvent.VK_F:
                weaponRouter.route("/fireWeapon", Params.of()); break;
            case KeyEvent.VK_R:
                if (isGameOver) {
                    isGameOver = false;
                    App.content().urbanStrikeBackend().restartGame();
                } else {
                    weaponRouter.route("/reloadWeapon", Params.of());
                }
                break;
            case KeyEvent.VK_E:
                itemRouter.route("/pickUpItem", Params.of()); break;
            case KeyEvent.VK_C:
                App.content().urbanStrikeBackend().toggleCover(); break;
            case KeyEvent.VK_N:
                App.content().urbanStrikeBackend().toggleNight(); break;
            case KeyEvent.VK_ESCAPE:
                System.exit(0); break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
