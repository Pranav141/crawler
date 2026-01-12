package com.pranav;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.databind.ObjectMapper;
//import javax.swing.text.Document;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

//crawler will crawl the websites in bfs fashion
//the crawler thread will use the blocking queue as a reference to run until the task is not complete
// it will internally count how many websites it has visited using the current proxy. it will visit
//10 threads 10 proxies and 10000 websites to scrap
//if we visited 1 website per second using one proxy then we will adhere to the robots rule and at the same time we will be using
// proxies with rotation to be sure that i dont run out of data limit on m proxies.

// crawller will be a seperate entity and crawling service will be seperate entity
//crawller will push the links to the queue while checking the condition and making sure that visited links are not visited again.


//bfs crawling
public class Crawler implements Runnable{
    public CrawlerService crawlerService;
    public Proxy proxy;
    public int count;
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public Crawler(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    private static final Pattern EXCLUDED_EXTENSIONS =
            Pattern.compile(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg|ico|tiff|pdf|zip|gz|exe)$",
                    Pattern.CASE_INSENSITIVE);

    private boolean shouldSkip(String url) {
        // 1. Check for fragments (internal page anchors)
        if (url.contains("#")) {
            return true;
        }

        // 2. Check for media/binary extensions
        if (EXCLUDED_EXTENSIONS.matcher(url).matches()) {
            return true;
        }

        return false;
    }

//    public void crawl(String url, int depth, CrawlerService crawlerService) throws IOException {
//
//    }

    @Override
    public void run() {
        if(proxy == null){
            try {
                proxy = crawlerService.getProxy();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        while(!crawlerService.isEmpty()){ //check queue is empty or not
            if(count >= 5){ // proxy rotation after hitting 5 urls using a proxy
                crawlerService.returnProxy(proxy,true);
                System.out.printf("%s[ROTATE]   %-15s | Returned: %-15s | Hits: %d%s%n",
                        LogColors.YELLOW,
                        Thread.currentThread().getName().toUpperCase(),
                        proxy.getIp(),
                        count,
                        LogColors.RESET
                );
                try {
                    proxy = crawlerService.getProxy();
                    count = 0;
                    System.out.printf("%s[ACQUIRE]  %-15s | New Proxy: %-15s | Status: READY%s%n",
                            LogColors.GREEN,
                            Thread.currentThread().getName().toUpperCase(),
                            proxy.getIp(),
                            LogColors.RESET
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            String url = crawlerService.getUrl();

            Document doc = null;
            try {
                java.net.Proxy proxy1;
                proxy1 = new java.net.Proxy(java.net.Proxy.Type.HTTP,new InetSocketAddress(proxy.getIp(),proxy.getPort()));
                String auth = proxy.username+":"+proxy.password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                doc = Jsoup.connect(url)
                        .proxy(proxy1)
                        .header("Proxy-Authorization", "Basic " + encodedAuth)
                        .timeout(5000) // 5 seconds
                        .get();
                System.out.printf(ANSI_CYAN + "[CRAWL]    %-15s | %-15s | %s" + ANSI_RESET + "%n",
                        Thread.currentThread().getName().toUpperCase(),
                        proxy.getIp(),
                        url
                );
                count++;
                crawlerService.alreadyVisitedLinks.add(url); //adding to the set that have already been visited
                // SUCCESS: Process your wiki page

            } catch (HttpStatusException e) {
                // CASE: TARGET URL ISSUE
                // The proxy worked fine, but the website said "No" (404, 403, 500)
                System.out.printf("%s[SITE_ERR]  %-15s | Status: %d | URL: %s%s%n",
                        LogColors.YELLOW,
                        Thread.currentThread().getName().toUpperCase(),
                        e.getStatusCode(),
                        e.getUrl(),
                        LogColors.RESET
                );                // Strategy: Don't blame the proxy. Just stop trying this specific URL.
//                continue;

            } catch (ConnectException | SocketTimeoutException e) {
                // CASE: PROXY ISSUE
                // The code couldn't even "reach" the server.
                System.out.printf("%s[PROXY_FAIL] %-15s | Reason: %-20s | Action: REPLACING%s%n",
                        LogColors.RED_BOLD,
                        Thread.currentThread().getName().toUpperCase(),
                        e.getMessage(),
                        LogColors.RESET
                );
                // Strategy: Mark this proxy as "dead" and ask the service for a new one.
                crawlerService.returnProxy(proxy,false);

            } catch (IOException e) {
                // CASE: GENERAL ISSUE
                if (e.getMessage().contains("407")) {
                    System.out.printf("%s[AUTH_FAIL] %-15s | Message: PROXY AUTHENTICATION REQUIRED (407) | Action: CHECK CREDENTIALS%s%n",
                            LogColors.RED_BOLD,
                            Thread.currentThread().getName().toUpperCase(),
                            LogColors.RESET
                    );
                } else {

                    System.out.printf("%s[ERROR]     %-15s | Unexpected Error: %-30s | Status: THREAD INTERRUPTED%s%n",
                            LogColors.RED_BOLD,
                            Thread.currentThread().getName().toUpperCase(),
                            e.getMessage(),
                            LogColors.RESET
                    );
                }
            }

            //send bodyContent to textProcessing pipeline using kafka
            if(doc!=null) {
                crawlerService.sendData(url.toLowerCase(),doc.getElementById("bodyContent").text(),doc.title());
            }

            // -------------------------------Adding the links to the queue-----------------------------------------
            Elements links = doc.getElementsByTag("a");

            for (Element link : links) {
                String urlTemp = link.absUrl("href");
                String shortUrl = link.attr("href");

                String title = link.attr("title");

                if(shouldSkip(urlTemp)){
                    continue;
                }
                if(crawlerService.isAllowed(shortUrl) && urlTemp.startsWith("https://en.wikipedia.org") && !crawlerService.contains(urlTemp.toLowerCase())){
                    try {
                        crawlerService.addUrl(urlTemp);
                    } catch (InterruptedException e) {
                    }
                }

            }
            try {
                Thread.sleep(750);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


}

