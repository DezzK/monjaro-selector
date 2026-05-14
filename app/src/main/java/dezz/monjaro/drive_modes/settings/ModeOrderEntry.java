package dezz.monjaro.drive_modes.settings;

public final class ModeOrderEntry {

    public final int code;
    public boolean enabled;

    public ModeOrderEntry(int code, boolean enabled) {
        this.code = code;
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModeOrderEntry)) return false;
        ModeOrderEntry that = (ModeOrderEntry) o;
        return code == that.code && enabled == that.enabled;
    }

    @Override
    public int hashCode() {
        return code * 31 + (enabled ? 1 : 0);
    }
}
