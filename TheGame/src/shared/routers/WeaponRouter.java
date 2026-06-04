package shared.routers;

import base.Params;
import base.SubRouter;
import my_base.App;

public class WeaponRouter implements SubRouter {

    @Override
    public Object route(String subPath, Params p) {
        switch (subPath) {
            case "/fireWeapon":
                App.content().urbanStrikeBackend().fireWeapon();
                return null;
            case "/reloadWeapon":
                App.content().urbanStrikeBackend().reloadWeapon();
                return null;
            default:
                throw new RuntimeException("Unknown weapon route: " + subPath);
        }
    }
}
