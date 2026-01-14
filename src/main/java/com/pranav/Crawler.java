package com.pranav;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;

import java.util.regex.Pattern;

public class Crawler implements Runnable{
    public CrawlerService crawlerService;
    public Proxy proxy;
    public int count;
    public int queueCount;
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

    @Override
    public void run() {

        while(!Thread.currentThread().isInterrupted()){
            queueCount = 0;
            //stops auto automatically when the executor service gives thread an interrupt.
            if(proxy == null){
                try {
                    proxy = crawlerService.getProxy();
                    System.out.printf("%s[ACQUIRE]  %-15s | New Proxy: %-15s | Status: READY%s%n",
                            LogColors.GREEN,
                            Thread.currentThread().getName().toUpperCase(),
                            proxy.getIp(),
                            LogColors.RESET
                    );
                    if(proxy == null){
                        throw new IOException();
                    }
                } catch (IOException e) {
                    System.out.println("Failed to get a Proxy when proxy is null");
//                    throw new RuntimeException(e);
                    continue;
                }
            }
            // proxy rotation after hitting 5 urls using a proxy
            if(count >= 5){
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
                    System.out.println(Thread.currentThread().getName().toUpperCase() + " Failed to get a Proxy when count exceed");
//                    throw new RuntimeException(e);
                    continue;
                }
            }
            String url = null;
            try {
                synchronized (crawlerService){
                    
                    url = crawlerService.getUrl();
                }
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName().toUpperCase() + " Failed to get an url from the queue");
//                throw  new RuntimeException(e);
            }
            Document doc = null;
            try {
                java.net.Proxy proxyObj;
                proxyObj = new java.net.Proxy(java.net.Proxy.Type.HTTP,new InetSocketAddress(proxy.getIp(),proxy.getPort()));
                String auth = proxy.username+":"+proxy.password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                doc = Jsoup.connect(url)
                        .proxy(proxyObj)
                        .header("Proxy-Authorization", "Basic " + encodedAuth)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
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
                continue;

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
                proxy = null;
                continue;

            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.printf("%s[SHUTDOWN]  %-15s | Stop signal detected during network call. Exiting.%s%n",
                            LogColors.PURPLE, Thread.currentThread().getName().toUpperCase(), LogColors.RESET);
                    return; // EXIT the run() method entirely. Do not 'continue'.
                }

                // IF NOT INTERRUPTED: Handle the actual network error
                if (e instanceof SocketTimeoutException || e instanceof ConnectException) {
                    System.out.printf("%s[PROXY_FAIL] %-15s | Reason: %-20s | Action: REPLACING%s%n",
                            LogColors.RED_BOLD,
                            Thread.currentThread().getName().toUpperCase(),
                            e.getMessage(),
                            LogColors.RESET
                    );
                    crawlerService.returnProxy(proxy, false);
                    proxy = null;
                    // No 'continue' needed here if it's the end of your try block,
                    // but it will loop back to the top of while(!isEmpty)
                }
            }


            if(doc==null) {

                continue;
            }
            //send bodyContent to textProcessing pipeline using kafka
            try {
                crawlerService.sendData(url.toLowerCase(),doc.getElementById("bodyContent").text(),doc.title());
            }
            catch (IllegalStateException e){
                System.out.println(Thread.currentThread().getName().toUpperCase() + " Failed to send data to the kafka queue");
                Thread.currentThread().interrupt();
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
                if(queueCount > 100){
                    break;
                }
                synchronized (crawlerService){
                    if(crawlerService.isAllowed(shortUrl) && urlTemp.startsWith("https://en.wikipedia.org") && !crawlerService.contains(urlTemp.toLowerCase())){
                        try {
                            crawlerService.addUrl(urlTemp);
                            queueCount++;
                        } catch (InterruptedException e) {
                            System.out.println(Thread.currentThread().getName().toUpperCase() + " Not able to add the url to the queue");
                        }
                    }
                }

            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName().toUpperCase() + " Failed to make the thread sleep for 1 seconds");
                throw new RuntimeException(e);
            }
        }
    }


}


