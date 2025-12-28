package com.pranav;

import java.util.ArrayList;
import java.util.List;

public class Rules {
    private List<String> allowed;
    private List<String> disallowed;
    private String userAgent;
    private Integer crawlDelay;

    public Rules(String userAgent) {
        this.allowed = new ArrayList<>();
        this.disallowed = new ArrayList<>();
        this.userAgent = userAgent;
    }

    public List<String> getAllowed() {
        return allowed;
    }

    public List<String> getDisallowed() {
        return disallowed;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Integer getCrawlDelay() {
        return crawlDelay;
    }

    public void setCrawlDelay(Integer crawlDelay) {
        this.crawlDelay = crawlDelay;
    }

    public void addAllowed(String domain){
        allowed.add(domain);
    }

    public void addDisallowed(String domain){
        disallowed.add(domain);
    }
}
