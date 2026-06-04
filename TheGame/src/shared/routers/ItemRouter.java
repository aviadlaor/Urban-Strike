package shared.routers;

import base.Params;
import base.SubRouter;
import my_base.App;

public class ItemRouter implements SubRouter {

    @Override
    public Object route(String subPath, Params p) {
        switch (subPath) {
            case "/pickUpItem":
                App.content().urbanStrikeBackend().processPickUpItem();
                return null;
            default:
                throw new RuntimeException("Unknown item route: " + subPath);
        }
    }
}
