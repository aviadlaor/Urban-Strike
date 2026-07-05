package ai.model;

public class Weapon {
    private static final int RELOAD_TICKS = 20;

    private final String weaponType;
    private int ammoInClip;
    private final int maxClipSize;
    private final int damagePerShot;
    private boolean isReloading = false;
    private int reloadTicksRemaining = 0;

    public Weapon(String weaponType, int ammoInClip, int maxClipSize, int damagePerShot) {
        this.weaponType   = weaponType;
        this.ammoInClip   = ammoInClip;
        this.maxClipSize  = maxClipSize;
        this.damagePerShot = damagePerShot;
    }

    public boolean decrementAmmo() {
        if (isReloading || ammoInClip <= 0) return false;
        ammoInClip--;
        return true;
    }

    public boolean reload() {
        if (isReloading || ammoInClip >= maxClipSize) return false;
        isReloading = true;
        reloadTicksRemaining = RELOAD_TICKS;
        return true;
    }

    public void tickReload() {
        if (!isReloading) return;
        if (--reloadTicksRemaining <= 0) {
            ammoInClip = maxClipSize;
            isReloading = false;
        }
    }

    public void addAmmo(int amount) {
        ammoInClip = Math.min(maxClipSize, ammoInClip + amount);
    }

    private boolean powered = false;
    public boolean isPowered()          { return powered; }
    public void setPowered(boolean p)   { this.powered = p; }
    public int getDamage()              { return powered ? damagePerShot * 2 : damagePerShot; }

    public boolean isReloading()    { return isReloading; }
    public boolean isFull()         { return ammoInClip >= maxClipSize; }
    public int getAmmo()            { return ammoInClip; }
    public int getMaxClipSize()     { return maxClipSize; }
    public String getWeaponType()   { return weaponType; }
}
