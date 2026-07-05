package ai.model;

public class CityElement {
    public enum Type { BUSH, TREE, LAMPPOST, CAR, TRASH_CAN }

    private final Type   type;
    private final double x, y;

    public CityElement(Type type, double x, double y) {
        this.type = type;
        this.x    = x;
        this.y    = y;
    }

    public Type   getType() { return type; }
    public double getX()    { return x; }
    public double getY()    { return y; }

    public int getRadius() {
        switch (type) {
            case BUSH:      return 25;
            case TREE:      return 30;
            case LAMPPOST:  return 12;
            case CAR:       return 55;
            case TRASH_CAN: return 18;
            default:        return 20;
        }
    }
}
