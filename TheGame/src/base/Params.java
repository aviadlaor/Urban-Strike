package base;

public final class Params {
    private final Object[] v;

    private Params(Object[] v) {
        this.v = v;
    }

    public static Params of(Object... v) {
        return new Params(v);
    }

    public int getInt(int i) {
        Object x = v[i];
        if (x instanceof Number)
            return ((Number) x).intValue();
        return Integer.parseInt(String.valueOf(x));
    }

    public double getDouble(int i) {
        Object x = v[i];
        if (x instanceof Number)
            return ((Number) x).doubleValue();
        return Double.parseDouble(String.valueOf(x));
    }

    public boolean getBoolean(int i) {
        Object x = v[i];
        if (x instanceof Boolean)
            return (Boolean) x;
        return Boolean.parseBoolean(String.valueOf(x));
    }

    public String getString(int i) {
        Object x = v[i];
        return x == null ? "" : String.valueOf(x);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Params[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}