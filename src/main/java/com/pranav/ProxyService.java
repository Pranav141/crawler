package com.pranav;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.RequestAuthenticator;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.util.*;
import java.util.concurrent.*;

public class ProxyService {
//    public List<Proxy> workingProxy = new ArrayList<>();
    private Queue<Proxy> unTestedProxy = new ConcurrentLinkedQueue<>();
    private Queue<Proxy> workingProxy = new ConcurrentLinkedQueue<>();
//    private Set<Proxy> beingTested = ConcurrentHashMap.newKeySet();
    private int NUM_TESTERS = 5;
    private ScheduledExecutorService healthChecker = Executors.newScheduledThreadPool(NUM_TESTERS);
    public void loadProxy() throws NullPointerException {
        try{
            InputStream file =  ProxyService.class.getClassLoader().getResourceAsStream("all-proxies.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
            List<String> proxies = bufferedReader.lines().toList();
            System.out.println(proxies);
            for (int i = 0; i < proxies.size(); i++) {
                String[] splits = proxies.get(i).split(":");
                String ip = splits[0];
                Integer port = Integer.parseInt(splits[1]);
                String username = splits[2];
                String password = splits[3];
                Proxy proxy = new Proxy(ip,port,username,password);
                unTestedProxy.offer(proxy);
            }
        } catch (NullPointerException e) {

        }

    }
    public void startHealthChecker() {
        for (int i = 0; i < NUM_TESTERS; i++) {
            try{
                healthChecker.scheduleWithFixedDelay(
                        this::testUntestedProxy,
                         1000L, // Stagger start times
                        2000,    // Test every 2 seconds
                        TimeUnit.MILLISECONDS
                );
            } catch (Exception e) {
                System.out.println();
            }
        }
    }

    public void testUntestedProxy()  {
        Proxy proxy = unTestedProxy.poll();
        if(unTestedProxy.isEmpty()){
            System.out.println("No untestedProxy available");

            System.out.println(workingProxy);
            System.out.println(workingProxy.size());
            return;
        }
        try{
            if(testProxy(proxy)){
                workingProxy.offer(proxy);
            }
            else{
//                unTestedProxy.offer(proxy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean testProxy(Proxy proxy)  {
        try{
            System.out.println("testing the following proxy "+proxy.toString());
            java.net.Proxy proxy1;
            proxy1 = new java.net.Proxy(java.net.Proxy.Type.HTTP,new InetSocketAddress(proxy.getIp(),proxy.getPort()));
            String auth = proxy.username+":"+proxy.password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            Connection.Response response = Jsoup.connect("https://en.wikipedia.org")
//                    .auth(myAuthenticator)
                    .header("Proxy-Authorization", "Basic " + encodedAuth)
                    .proxy(proxy1)
                    .timeout(7500)
                    .ignoreHttpErrors(true)
                    .execute();
            System.out.println(response.statusCode());
            if(response.statusCode() == 200){
                return true;
            }
        } catch (Exception e) {
            System.out.println(proxy.toString() + "Had an exception " + e.getMessage());
        }
        return false;
    }

    public Proxy getProxy() throws IOException {
        if(workingProxy.isEmpty()){
            System.out.println("There is no working proxies currently available");
            return null;
        }
        return workingProxy.poll();
    }
    static void main() throws InterruptedException {
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        ProxyService proxyService = new ProxyService();
        proxyService.loadProxy();
        proxyService.startHealthChecker();
//        Thread.sleep(30000);
//        System.out.println(proxyService.workingProxy.toArray()+"here");
//        System.out.println(proxyService.workingProxy);
    }

}


//package com.pranav;
//
//import org.jsoup.Jsoup;
//import java.io.*;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class ProxyService {
//    // Proxy pools
//    private final Queue<Proxy> workingProxies;           // Hot pool of verified working proxies
//    private final Queue<Proxy> untestedProxies;          // Proxies that need testing
//    private final Set<Proxy> beingTested;                // Currently being tested
//
//    // Statistics tracking
//    private final ConcurrentHashMap<Proxy, ProxyStats> proxyStats;
//
//    // Background testers
//    private final ScheduledExecutorService healthChecker;
//    private final int NUM_TESTERS = 3;
//
//    // Configuration
//    private static final String TEST_URL = "https://en.wikipedia.org/wiki/Main_Page";
//    private static final int TEST_TIMEOUT = 5000; // 5 seconds
//    private static final int MIN_SUCCESS_RATE = 50; // 50% success rate threshold
//
//    public ProxyService() {
//        this.workingProxies = new ConcurrentLinkedQueue<>();
//        this.untestedProxies = new ConcurrentLinkedQueue<>();
//        this.beingTested = ConcurrentHashMap.newKeySet();
//        this.proxyStats = new ConcurrentHashMap<>();
//        this.healthChecker = Executors.newScheduledThreadPool(NUM_TESTERS);
//    }
//
//    /**
//     * Inner class to track proxy statistics
//     */
//    static class ProxyStats {
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failureCount = new AtomicInteger(0);
//        AtomicInteger consecutiveFailures = new AtomicInteger(0);
//        long lastUsed = System.currentTimeMillis();
//        long lastTested = System.currentTimeMillis();
//
//        void recordSuccess() {
//            successCount.incrementAndGet();
//            consecutiveFailures.set(0);
//            lastUsed = System.currentTimeMillis();
//        }
//
//        void recordFailure() {
//            failureCount.incrementAndGet();
//            consecutiveFailures.incrementAndGet();
//        }
//
//        int getSuccessRate() {
//            int total = successCount.get() + failureCount.get();
//            return total == 0 ? 0 : (successCount.get() * 100) / total;
//        }
//
//        boolean shouldRetire() {
//            // Retire if 5 consecutive failures or very low success rate
//            return consecutiveFailures.get() >= 5 ||
//                    (getTotalAttempts() > 10 && getSuccessRate() < 20);
//        }
//
//        int getTotalAttempts() {
//            return successCount.get() + failureCount.get();
//        }
//    }
//
//    /**
//     * Load proxies from file
//     */
//    public void loadProxies() throws IOException {
//        try {
//            InputStream file = ProxyService.class.getClassLoader()
//                    .getResourceAsStream("all-proxies.txt");
//
//            if (file == null) {
//                throw new FileNotFoundException("all-proxies.txt not found in resources");
//            }
//
//            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
//            List<String> proxyLines = bufferedReader.lines().toList();
//            bufferedReader.close();
//
//            System.out.println("Loading " + proxyLines.size() + " proxies...");
//
//            for (String line : proxyLines) {
//                try {
//                    String[] parts = line.trim().split(":");
//                    if (parts.length >= 3) {
//
//                        String protocol = parts[0];
//                        if(protocol.contains("socks")){
//                            continue;
//                        }
//                        String ip = parts[1].replace("//", "");
//                        int port = Integer.parseInt(parts[2]);
//
//                        Proxy proxy = new Proxy(ip, port, protocol);
//                        untestedProxies.offer(proxy);
//                        proxyStats.put(proxy, new ProxyStats());
//                    }
//                } catch (Exception e) {
//                    System.err.println("Error parsing proxy line: " + line);
//                }
//            }
//
//            System.out.println("Loaded " + untestedProxies.size() + " proxies for testing");
//
//        } catch (Exception e) {
//            throw new IOException("Failed to load proxies", e);
//        }
//    }
//
//    /**
//     * Start background health checking
//     */
//    public void startHealthChecker() {
//        System.out.println("Starting proxy health checker with " + NUM_TESTERS + " threads...");
//
//        // Initial testing of untested proxies
//        for (int i = 0; i < NUM_TESTERS; i++) {
//            healthChecker.scheduleWithFixedDelay(
//                    this::testUntestedProxies,
//                    i * 1000, // Stagger start times
//                    2000,     // Test every 2 seconds
//                    TimeUnit.MILLISECONDS
//            );
//        }
//
//        // Periodic retesting of working proxies
//        healthChecker.scheduleWithFixedDelay(
//                this::retestWorkingProxies,
//                30000,  // Start after 30 seconds
//                60000,  // Retest every 60 seconds
//                TimeUnit.MILLISECONDS
//        );
//
//        // Statistics reporter
//        healthChecker.scheduleWithFixedDelay(
//                this::printStats,
//                10000,  // First report after 10 seconds
//                30000,  // Report every 30 seconds
//                TimeUnit.MILLISECONDS
//        );
//    }
//
//    /**
//     * Test proxies from untested queue
//     */
//    private void testUntestedProxies() {
//        Proxy proxy = untestedProxies.poll();
//        if (proxy != null && beingTested.add(proxy)) {
//            try {
//                if (testProxy(proxy)) {
//                    workingProxies.offer(proxy);
//                    proxyStats.get(proxy).recordSuccess();
//                    System.out.println("✓ Proxy working: " + proxy);
//                } else {
//                    proxyStats.get(proxy).recordFailure();
//
//                    // Give it another chance later if not too many failures
//                    if (!proxyStats.get(proxy).shouldRetire()) {
//                        untestedProxies.offer(proxy);
//                    } else {
//                        System.out.println("✗ Retired proxy: " + proxy +
//                                " (success rate: " + proxyStats.get(proxy).getSuccessRate() + "%)");
//                    }
//                }
//            } finally {
//                beingTested.remove(proxy);
//            }
//        }
//    }
//
//    /**
//     * Retest working proxies to ensure they're still functional
//     */
//    private void retestWorkingProxies() {
//        List<Proxy> toRetest = new ArrayList<>();
//        Proxy proxy;
//
//        // Drain working proxies for retesting
//        while ((proxy = workingProxies.poll()) != null) {
//            toRetest.add(proxy);
//        }
//
//        for (Proxy p : toRetest) {
//            ProxyStats stats = proxyStats.get(p);
//            long timeSinceLastTest = System.currentTimeMillis() - stats.lastTested;
//
//            // Retest if not tested recently or has declining success rate
//            if (timeSinceLastTest > 60000 || stats.getSuccessRate() < MIN_SUCCESS_RATE) {
//                if (testProxy(p)) {
//                    stats.recordSuccess();
//                    workingProxies.offer(p);
//                } else {
//                    stats.recordFailure();
//                    if (!stats.shouldRetire()) {
//                        untestedProxies.offer(p); // Try to fix it
//                    }
//                }
//                stats.lastTested = System.currentTimeMillis();
//            } else {
//                // Still good, put back
//                workingProxies.offer(p);
//            }
//        }
//    }
//
//    /**
//     * Test if a proxy is working
//     */
//    private boolean testProxy(Proxy proxy) {
//        try {
//            org.jsoup.Connection.Response response = Jsoup.connect(TEST_URL)
//                    .proxy(proxy.getIp(), proxy.getPort())
//                    .timeout(TEST_TIMEOUT)
//                    .ignoreHttpErrors(true)
//                    .execute();
//
//            return response.statusCode() == 200;
//
//        } catch (Exception e) {
//            // Proxy failed
//            return false;
//        }
//    }
//
//    /**
//     * Get a working proxy (blocking with timeout)
//     * This is the main method crawlers will call
//     */
//    public Proxy getProxy() throws InterruptedException {
//        return getProxy(30, TimeUnit.SECONDS);
//    }
//
//    /**
//     * Get a working proxy with timeout
//     */
//    public Proxy getProxy(long timeout, TimeUnit unit) throws InterruptedException {
//        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
//
//        while (System.currentTimeMillis() < deadline) {
//            Proxy proxy = workingProxies.poll();
//
//            if (proxy != null) {
//                // Double-check it's still working if last used was a while ago
//                ProxyStats stats = proxyStats.get(proxy);
//                long timeSinceLastUse = System.currentTimeMillis() - stats.lastUsed;
//
//                if (timeSinceLastUse < 300000) { // 5 minutes
//                    // Recently used, likely still good
//                    return proxy;
//                } else {
//                    // Been a while, quick retest
//                    if (testProxy(proxy)) {
//                        stats.recordSuccess();
//                        return proxy;
//                    } else {
//                        stats.recordFailure();
//                        untestedProxies.offer(proxy);
//                    }
//                }
//            } else {
//                // No proxies available, wait a bit
//                System.out.println("⏳ Waiting for working proxies... (Working: " +
//                        workingProxies.size() + ", Untested: " + untestedProxies.size() + ")");
//                Thread.sleep(2000);
//            }
//        }
//
//        throw new IllegalStateException("No working proxies available after timeout");
//    }
//
//    /**
//     * Return a proxy after use with status report
//     */
//    public void returnProxy(Proxy proxy, boolean wasSuccessful) {
//        if (proxy == null) return;
//
//        ProxyStats stats = proxyStats.get(proxy);
//        if (stats != null) {
//            if (wasSuccessful) {
//                stats.recordSuccess();
//                workingProxies.offer(proxy); // Return to working pool
//            } else {
//                stats.recordFailure();
//                if (stats.shouldRetire()) {
//                    System.out.println("✗ Retiring proxy after failure: " + proxy);
//                } else {
//                    untestedProxies.offer(proxy); // Retest it
//                }
//            }
//        }
//    }
//
//    /**
//     * Print statistics
//     */
//    private void printStats() {
//        System.out.println("\n===== Proxy Pool Statistics =====");
//        System.out.println("Working proxies: " + workingProxies.size());
//        System.out.println("Untested proxies: " + untestedProxies.size());
//        System.out.println("Being tested: " + beingTested.size());
//
//        // Show top 5 best proxies
//        List<Map.Entry<Proxy, ProxyStats>> sorted = new ArrayList<>(proxyStats.entrySet());
//        sorted.sort((a, b) -> Integer.compare(
//                b.getValue().getSuccessRate(),
//                a.getValue().getSuccessRate()
//        ));
//
//        System.out.println("\nTop 5 proxies:");
//        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
//            Map.Entry<Proxy, ProxyStats> entry = sorted.get(i);
//            ProxyStats stats = entry.getValue();
//            if (stats.getTotalAttempts() > 0) {
//                System.out.printf("%d. %s - Success: %d%% (%d/%d attempts)%n",
//                        i + 1,
//                        entry.getKey(),
//                        stats.getSuccessRate(),
//                        stats.successCount.get(),
//                        stats.getTotalAttempts()
//                );
//            }
//        }
//        System.out.println("================================\n");
//    }
//
//    /**
//     * Get pool sizes for monitoring
//     */
//    public int getWorkingProxyCount() {
//        return workingProxies.size();
//    }
//
//    public int getUntestedProxyCount() {
//        return untestedProxies.size();
//    }
//
//    /**
//     * Shutdown health checker
//     */
//    public void shutdown() {
//        System.out.println("Shutting down proxy health checker...");
//        healthChecker.shutdown();
//        try {
//            if (!healthChecker.awaitTermination(10, TimeUnit.SECONDS)) {
//                healthChecker.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            healthChecker.shutdownNow();
//        }
//    }
//
//    /**
//     * Main method for testing
//     */
//    public static void main(String[] args) throws Exception {
//        ProxyService proxyService = new ProxyService();
//
//        // Load proxies from file
//        proxyService.loadProxies();
//
//        // Start background health checking
//        proxyService.startHealthChecker();
//
//        // Wait for some proxies to be tested
//        System.out.println("Waiting for proxies to be tested...");
//        Thread.sleep(10000);
//
//        // Test getting proxies
//        System.out.println("\nTesting proxy retrieval:");
//        for (int i = 0; i < 5; i++) {
//            try {
//                Proxy proxy = proxyService.getProxy();
//                System.out.println("Got proxy: " + proxy);
//
//                // Simulate usage
//                Thread.sleep(1000);
//
//                // Return it as successful
//                proxyService.returnProxy(proxy, true);
//            } catch (Exception e) {
//                System.err.println("Failed to get proxy: " + e.getMessage());
//            }
//        }
//
//        // Let it run for a bit to see statistics
//        Thread.sleep(30000);
//
//        // Shutdown
//        proxyService.shutdown();
//    }
//}
