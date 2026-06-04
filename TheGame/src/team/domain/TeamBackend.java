package team.domain;

import shared.ui_ports.TeamUiPort;

public class TeamBackend {

    /**
     * Use ex3UiPort() as a function and not a variableto get the UI port
     * to avoid trying to get it before it was set up by the UI
     * (which happens at UI startup, but this backend is constructed at app
     * startup).
     */
    private TeamUiPort teamUiPort() {
        return TeamUiPort.getInstance();
    }

    // Called once at UI startup
    public void start() {
        System.out.println("TeamBackend started");
        teamUiPort().log("Logging: TeamBackend started");
        System.out.println("Calling UI method1 with elementId 42 ...");
        teamUiPort().method1(42);
    }

    // UI input events call these via router
    public void doStep(int step) {
        System.out.println("In TeamBackend doStep ...");
        System.out.println("Calling UI method1 with elementId " + step + " ...");
        teamUiPort().method1(step);
    }
}