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
    public static Map<String,Integer> dict ;

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


    public static void crawl(String url,int depth,TextProcessor textProcessor) throws IOException {
        if(hs.contains(url) || depth >1){
            return;
        }
        Document doc = Jsoup.connect(url).get();
        hs.add(url);
        System.out.println(url);
//        dict = textProcessor.process(doc.body().text(),url);

        int count = 0;

        Elements newsHeadlines = doc.getElementsByTag("a");

        for (Element headline : newsHeadlines) {
            if(count>5){
                break;
            }
            String urlTemp = headline.absUrl("href");
            String shortUrl = headline.attr("href");
            String title = headline.attr("title");
            if(urlTemp.endsWith(".jpg") || shortUrl.equals("#")){
                continue;
            }
            if(isAllowed(shortUrl) && urlTemp.startsWith("https://en.wikipedia.org") && !hs.contains(urlTemp)){
                System.out.println(shortUrl);
                count += 1;
                crawl(urlTemp,depth+1,textProcessor);
            }

        }
        System.out.println(newsHeadlines.size()+" "+count);
    }


    public static boolean isAllowed(String path) {


        Rules rules = hm.getOrDefault("PranavWikiCrawler", hm.get("*"));

        if (rules == null) return true;

        String bestMatch = null;
        boolean allow = true;
        for (String dis : rules.getDisallowed()) {

            if (path.startsWith(dis)) {
                if (bestMatch == null || dis.length() > bestMatch.length()) {
                    bestMatch = dis;
                    allow = false;
                }
            }
        }

        for (String al : rules.getAllowed()) {
            if (path.startsWith(al)) {
                if (bestMatch == null || al.length() >= bestMatch.length()) {
                    bestMatch = al;
                    allow = true;
                }
            }
        }
        return allow;
    }
    static void main() throws IOException {
        setRobotsText("https://en.wikipedia.org/");
        TextProcessor textProcessor = new TextProcessor();
        Crawler.crawl("https://en.wikipedia.org/",0,textProcessor);
//        String temp = "https://en.wikipedia.org";
//        System.out.println(temp.length());
        Iterator<Map.Entry<String,Integer>> iterator =dict.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, Integer> entry = iterator.next();
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }

    }
}

