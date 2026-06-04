package ai.ui;

import base.Params;
import my_base.App;
import shared.MainRouter;
import shared.routers.ItemRouter;
import shared.routers.WeaponRouter;
import shared.ui_ports.TeamUiPort;

import javax.swing.*;
import java.awt.*;

public class Ui {

    private MainRouter mainRouter;
    private GamePanel gamePanel;
    private TeamUiPortImpl uiPortImpl;

    // הפונקציה שהייתה חסרה ל-App.java של השלד
    public void setUiPorts() {
        // אין צורך לעשות כאן כלום, האתחול האמיתי קורה ב-start
    }

    public void start(MainRouter mainRouter) {
        this.mainRouter = mainRouter;
        
        SwingUtilities.invokeLater(() -> {
            createWindow();
            mainRouter.route("/team/start", Params.of());
        });
}

    private void createWindow() {
        JFrame frame = new JFrame("Urban Strike");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        gamePanel = new GamePanel(mainRouter, App.weaponRouter(), App.itemRouter());
        frame.add(gamePanel, BorderLayout.CENTER);

        // Wire the UI port BEFORE setVisible so the very first native paint
        // event already has a renderer available (avoids black-flash on expose).
        uiPortImpl = new TeamUiPortImpl(gamePanel);
        gamePanel.setUiPort(uiPortImpl);
        TeamUiPort.setInstance(uiPortImpl);

        frame.setVisible(true);
        gamePanel.requestFocusInWindow();
    }
}