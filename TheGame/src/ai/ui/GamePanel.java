package ai.ui;

import base.Params;
import shared.MainRouter;
import shared.routers.ItemRouter;
import shared.routers.WeaponRouter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GamePanel extends JPanel implements KeyListener {

    private final MainRouter   mainRouter;
    private final WeaponRouter weaponRouter;
    private final ItemRouter   itemRouter;
    private TeamUiPortImpl uiPort;

    public GamePanel(MainRouter mainRouter, WeaponRouter weaponRouter, ItemRouter itemRouter) {
        this.mainRouter   = mainRouter;
        this.weaponRouter = weaponRouter;
        this.itemRouter   = itemRouter;
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
    }

    public void setUiPort(TeamUiPortImpl uiPort) {
        this.uiPort = uiPort;
    }

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
                weaponRouter.route("/reloadWeapon", Params.of()); break;
            case KeyEvent.VK_E:
                itemRouter.route("/pickUpItem", Params.of()); break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
