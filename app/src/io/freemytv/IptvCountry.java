package io.freemytv;

import java.io.Serializable;

/**
 * Country metadata for IPTV channels.
 */
public final class IptvCountry implements Serializable {
    public final String code;
    public final String name;
    public final String flag;

    public IptvCountry(String code, String name, String flag) {
        this.code = code != null ? code.toUpperCase() : "";
        this.name = name != null ? name : "";
        this.flag = flag != null ? flag : "🌐";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IptvCountry)) return false;
        return code.equalsIgnoreCase(((IptvCountry) o).code);
    }

    @Override public int hashCode() {
        return code.toLowerCase().hashCode();
    }
}
