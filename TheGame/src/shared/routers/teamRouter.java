package shared.routers;

import base.Params;
import base.SubRouter;
import my_base.App;

public class teamRouter implements SubRouter {

    private final WeaponRouter weaponRouter = new WeaponRouter();
    private final ItemRouter   itemRouter   = new ItemRouter();

    public WeaponRouter getWeaponRouter() { return weaponRouter; }
    public ItemRouter   getItemRouter()   { return itemRouter; }

    @Override
    public Object route(String subPath, Params p) {
        switch (subPath) {
            case "/start":
                // startGame() is now triggered by the player pressing ENTER on the title screen
                return null;

            case "/movePlayer":
                App.content().urbanStrikeBackend().movePlayer(p.getString(0));
                return null;

            case "/fireWeapon":
            case "/reloadWeapon":
                return weaponRouter.route(subPath, p);

            case "/pickUpItem":
                return itemRouter.route(subPath, p);

            case "/setAngle":
                App.content().urbanStrikeBackend().setPlayerAngle(p.getDouble(0));
                return null;

            case "/rotatePlayer":
                App.content().urbanStrikeBackend().rotatePlayer(p.getDouble(0));
                return null;

            default:
                throw new RuntimeException("Unknown team route: " + subPath);
        }
    }
}
