package io.freemytv;

import java.io.Serializable;

/**
 * An IPTV channel stream item parsed from an M3U playlist.
 */
public final class IptvChannel implements Serializable {
    public final String id;
    public final String name;
    public final String logoUrl;
    public final String group;
    public final String url;
    public final java.util.List<String> backupUrls;
    public final String countryCode;
    public final String resolution;
    public boolean isStarred;

    public IptvChannel(String id, String name, String logoUrl, String group, String url, String countryCode, String resolution) {
        this(id, name, logoUrl, group, url, new java.util.ArrayList<>(), countryCode, resolution);
    }

    public IptvChannel(String id, String name, String logoUrl, String group, String url, java.util.List<String> backupUrls, String countryCode, String resolution) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "Channel";
        this.logoUrl = logoUrl != null ? logoUrl : "";
        this.group = group != null && !group.isEmpty() ? group : "General";
        this.url = url != null ? url.trim() : "";
        this.backupUrls = backupUrls != null ? backupUrls : new java.util.ArrayList<>();
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : "";
        this.resolution = resolution != null ? resolution : "";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IptvChannel)) return false;
        IptvChannel that = (IptvChannel) o;
        return url.equals(that.url);
    }

    @Override public int hashCode() {
        return url.hashCode();
    }
}
