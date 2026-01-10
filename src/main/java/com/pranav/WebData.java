package com.pranav;

public class WebData{
    public String url;
    public String text;
    public String title;
    public WebData() {
    }

    public WebData(String url, String text,String title) {
        this.url = url;
        this.text = text;
        this.title = title;
    }


    @Override
    public String toString() {
        return "WebData{" +
                "url='" + url + '\'' +
                ", text='" + text+ '\'' +
                '}';
    }
}