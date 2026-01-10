package com.pranav;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
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
    public Map<String,Rules> robotRules;
    public int NUM_CRAWLERS = 5;
    public ExecutorService es = Executors.newFixedThreadPool(NUM_CRAWLERS);
    public ProxyService proxyService;
    public CrawlerService() {
        waitingUrl = new LinkedBlockingQueue<>();
        alreadyVisitedLinks = ConcurrentHashMap.newKeySet();
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
        return alreadyVisitedLinks.contains(url);
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
        executor.shutdown();

        try {
            // 2. Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(1500, TimeUnit.MILLISECONDS)) {
                // 3. Force shutdown if they didn't finish in time
                executor.shutdownNow();

                // 4. Wait a bit more for tasks to respond to the interrupt
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            // 5. (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    public void startCrawling() throws InterruptedException{
        waitingUrl.add("https://en.wikipedia.org/wiki/Computer_Science");
        waitingUrl.add("https://en.wikipedia.org/wiki/History");
        waitingUrl.add("https://en.wikipedia.org/wiki/Biology");
        waitingUrl.add("https://en.wikipedia.org/wiki/Mathematics");
        waitingUrl.add("https://en.wikipedia.org/wiki/Literature");

        for(int i = 0;i<NUM_CRAWLERS;i++){
            es.submit(new Crawler(this));
        }
        Thread.sleep(5000);
        while(alreadyVisitedLinks.size() < 50){
            System.out.println(alreadyVisitedLinks.size() + "size of the hashset");
            Thread.sleep(500);
        }
        stopExecutor(es);
        proxyService.stopHealthChecker();

    }

    public boolean isEmpty(){
        return waitingUrl.isEmpty();
    }
    public String getUrl(){
        return waitingUrl.poll();
    }
    public void addUrl(String url) throws InterruptedException {
        waitingUrl.put(url);
    }

    public void returnProxy(Proxy proxy,boolean isWorking){
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

        String topic = "TEXT_PROCESSING_MOCK";
        ObjectMapper mapper = new ObjectMapper();
//        String message = "Hello from Java!";
        try {
            // Create the object and convert to JSON string
            WebData data = new WebData(url, message,title);
            String jsonValue = mapper.writeValueAsString(data);

//            String topic = "TEXT_PROCESSING_MOCK";

            producer.send(new ProducerRecord<>(topic, jsonValue), (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("title " + title );
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
        cs.proxyService.startProxyService();
        cs.setKafkaProperties();
        Thread.sleep(1000);
        cs.setRobotsText("https://en.wikipedia.org/");
        Thread.sleep(5000);
        cs.startCrawling();
//        System.out.println(cs.waitingUrl);
        System.out.println(cs.alreadyVisitedLinks.size());
        cs.closeKafka();
    }
}
