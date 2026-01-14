package com.pranav;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

public class CrawlerService {
    public DBConn conn = DBConn.getInstance();
    public KafkaProducer<String, String> producer;
    public BlockingQueue<String> waitingUrl;
    public Set<String> alreadyVisitedLinks;
    public Set<String> discoveredLinks;
    public Map<String,Rules> robotRules;
    public int NUM_CRAWLERS = 5;
    public int PAGESTOCRAWL = 150;
    public ExecutorService es = Executors.newFixedThreadPool(NUM_CRAWLERS);
    public ProxyService proxyService;
    public CrawlerService() {
        waitingUrl = new LinkedBlockingQueue<>();
        alreadyVisitedLinks = ConcurrentHashMap.newKeySet();
        discoveredLinks = ConcurrentHashMap.newKeySet();
        proxyService = new ProxyService();
        robotRules = new HashMap<>();
    }

    public void setRobotsText(String url) throws IOException {
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
                    robotRules.putIfAbsent(value,new Rules(value));
                    break;
                case "allow":
                    robotRules.get(currentAgent).addAllowed(value);
                    break;
                case "disallow":
                    robotRules.get(currentAgent).addDisallowed(value);
                    break;
                case "crawl-delay":
                    robotRules.get(currentAgent).setCrawlDelay(Integer.parseInt(value));
                    break;
                default:
                    continue;
            }
        }
    }
    public void loadVisitedUrl(){
        alreadyVisitedLinks = conn.loadVisitedUrl();
    }
    public boolean contains(String url){
        if(alreadyVisitedLinks.contains(url) || discoveredLinks.contains(url)){
            return true;
        }
        return false;
    }
    public boolean isAllowed(String path) {


        Rules rules = robotRules.getOrDefault("PranavWikiCrawler", robotRules.get("*"));

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

    public void stopExecutor(ExecutorService executor) {
        // 1. Stop accepting new tasks
        System.out.printf("%n%s[SHUTDOWN]  %-15s | Signal sent to ExecutorService.%s%n",
                LogColors.PURPLE, "CORE", LogColors.RESET);
        executor.shutdown();

        try {
            System.out.printf("%s[SHUTDOWN]  %-15s | Waiting 1s for threads to exit gracefully...%s%n",
                    LogColors.PURPLE, "CORE", LogColors.RESET);
            // 2. Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {

                System.out.printf("%s[SHUTDOWN]  %-15s | Time limit reached. Forcing immediate stop!%s%n",
                        LogColors.RED, "CORE", LogColors.RESET);
                // 3. Force shutdown if they didn't finish in time
                executor.shutdownNow();

                // 4. Wait a bit more for tasks to respond to the interrupt
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("[CRITICAL] Executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            // 5. (Re-)Cancel if current thread also interrupted
            System.out.printf("%s[SHUTDOWN]  %-15s | Interrupted during wait. Forcing shutdown.%s%n",
                    LogColors.RED, "CORE", LogColors.RESET);
            executor.shutdownNow();
        }
    }


    public void startCrawling() throws InterruptedException{
        waitingUrl.add("https://en.wikipedia.org/wiki/computer_science");
        waitingUrl.add("https://en.wikipedia.org/wiki/history");
        waitingUrl.add("https://en.wikipedia.org/wiki/biology");
        waitingUrl.add("https://en.wikipedia.org/wiki/mathematics");
        waitingUrl.add("https://en.wikipedia.org/wiki/literature");

        discoveredLinks.add("https://en.wikipedia.org/wiki/computer_science");
        discoveredLinks.add("https://en.wikipedia.org/wiki/history");
        discoveredLinks.add("https://en.wikipedia.org/wiki/biology");
        discoveredLinks.add("https://en.wikipedia.org/wiki/mathematics");
        discoveredLinks.add("https://en.wikipedia.org/wiki/literature");


        for(int i = 0;i<NUM_CRAWLERS;i++){
            es.submit(new Crawler(this));
        }
        Thread.sleep(6000);

        //while visited < size and queue is not empty
        while(alreadyVisitedLinks.size() < PAGESTOCRAWL && !waitingUrl.isEmpty()){
            Thread.sleep(750);
        }
        proxyService.stopHealthChecker();
        stopExecutor(es);

    }

    public boolean isEmpty(){
        return waitingUrl.isEmpty();
    }
    public String getUrl() throws InterruptedException {
        return waitingUrl.poll(4,TimeUnit.SECONDS);
    }
    public void addUrl(String url) throws InterruptedException {
        discoveredLinks.add(url.toLowerCase());
        waitingUrl.put(url);
    }

    public synchronized void returnProxy(Proxy proxy,boolean isWorking){
        if(isWorking){
            proxyService.addWorkingProxy(proxy);
        }else {
            proxyService.addUntesetedProxy(proxy);
        }
    }
    public Proxy getProxy() throws IOException {
        return proxyService.getProxy();
    }

    public void setKafkaProperties(){
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        producer = new KafkaProducer<>(props);
    }

    public void closeKafka(){
        producer.close();
    }

    public void sendData(String url,String message,String title){

        String topic = "TEXT_PROCESSING";
        ObjectMapper mapper = new ObjectMapper();
//        String message = "Hello from Java!";
        try {
            // Create the object and convert to JSON string
            WebData data = new WebData(url, message,title);
            String jsonValue = mapper.writeValueAsString(data);

//            String topic = "TEXT_PROCESSING_MOCK";

            producer.send(new ProducerRecord<>(topic, jsonValue), (metadata, exception) -> {
                if (exception == null) {
//                    System.out.println("title " + title );
                } else {
                    exception.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static void main() throws IOException, InterruptedException {
        CrawlerService cs = new CrawlerService();

        Instant start = Instant.now();
        cs.proxyService.startProxyService();
        cs.setKafkaProperties();
        Thread.sleep(1000);
        cs.setRobotsText("https://en.wikipedia.org/");
        Thread.sleep(5000);
        cs.startCrawling();
//        System.out.println(cs.alreadyVisitedLinks.size());
        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        Thread.sleep(5000);
        cs.closeKafka();
        System.out.printf("%s[STATS]    %-15s | %-12s : %d sec%s%n",
                LogColors.BLUE, "CRAWLER SYSTEM", "Elapsed", timeElapsed.toSeconds(), LogColors.RESET);

        System.out.printf("%s[STATS]    %-15s | %-12s : %d links%s%n",
                LogColors.BLUE, "CRAWLER SYSTEM", "Total Visited", cs.alreadyVisitedLinks.size(), LogColors.RESET);
//        System.out.println(cs.waitingUrl.size());

    }
}
