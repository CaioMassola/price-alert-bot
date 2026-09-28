package com.pricealert.monitor;
public record MonitorRequest(String query, String productUrl) {
    public static MonitorRequest search(String query) { return new MonitorRequest(query,null); }
    public static MonitorRequest product(String url) { return new MonitorRequest(null,url); }
}

