package com.pranav;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

//import javax.swing.text.Document;
import java.io.IOException;
import java.util.*;

public class Crawler {
    public static Set<String> hs = new HashSet<>();
    public static Map<String,Rules> hm= new HashMap<>();
    public static void setRobotsText(String url) throws IOException {
        Document doc = Jsoup.connect(url+"robots.txt").get();
        String rawText = doc.wholeText();
        String currentAgent = "";
        for(String raw : rawText.split("\n")){
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }

            String[] parts = raw.split(":", 2);
            if (parts.length != 2) continue;

            String key = parts[0].trim().toLowerCase();
            String value = parts[1].trim();

            switch(key){
                case "user-agent":
                    currentAgent = value;
//                    System.out.println(currentAgent);
                    hm.putIfAbsent(value,new Rules(value));
                    break;
                case "allow":
                    hm.get(currentAgent).addAllowed(value);
                    break;
                case "disallow":
                    hm.get(currentAgent).addDisallowed(value);
                    break;
                case "crawl-delay":
                    hm.get(currentAgent).setCrawlDelay(Integer.parseInt(value));
                    break;
                default:
                    continue;
            }
        }

    }


    public static void crawl(String url,int depth) throws IOException {
        if(hs.contains(url) || depth >1){
            return;
        }
        Document doc = Jsoup.connect(url).get();
        hs.add(url);
        int count = 0;
        Elements newsHeadlines = doc.getElementsByTag("a");

        for (Element headline : newsHeadlines) {
            String urlTemp = headline.absUrl("href");
            String shortUrl = headline.attr("href");
            String title = headline.attr("title");
            if(hm.get("*").getDisallowed().contains(shortUrl)){
                System.out.println("link disallowed");
            }
            if(urlTemp.startsWith("https://en.wikipedia.org/wiki/") && !urlTemp.endsWith(".jpg") && !shortUrl.equals("#")){
                System.out.println((headline.attr("title")+" "+ shortUrl));
                count += 1;
            }

//            crawl(urlTemp,depth+1);
        }
        System.out.println(newsHeadlines.size()+" "+count);
        createDict(doc.body().text());
//        System.out.println(doc.body().text()); /* Get the content from the website*/


    }
    public static void createDict(String text){
        Map<String,Integer> hm = new TreeMap<>();
        String[] arr = text.split(" ");
        for(String s : arr){
            hm.put(s,hm.getOrDefault(s,0)+1);
        }

    }
    static void main() throws IOException {
        setRobotsText("https://en.wikipedia.org/");
        Crawler.crawl("https://en.wikipedia.org/",0);
//        String temp = "https://en.wikipedia.org";
//        System.out.println(temp.length());
    }
}
