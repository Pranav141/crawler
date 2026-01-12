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
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    private Queue<Proxy> unTestedProxy = new ConcurrentLinkedQueue<>();
    private Queue<Proxy> workingProxy = new ConcurrentLinkedQueue<>();
    private int NUM_TESTERS = 5;
    private ScheduledExecutorService healthChecker = Executors.newScheduledThreadPool(NUM_TESTERS);
    public void loadProxy() throws NullPointerException {
        try{
            InputStream file =  ProxyService.class.getClassLoader().getResourceAsStream("all-proxies.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
            List<String> proxies = bufferedReader.lines().toList();
//            System.out.println(proxies);
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
        if(unTestedProxy.isEmpty()){
//            System.out.println("No untestedProxy available");
            return;
        }
        Proxy proxy = unTestedProxy.poll();
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
//            System.out.println("Testing the Following "+proxy.toString().toUpperCase());

//            System.out.printf("[CHECKING] %-15s | Target: %-20s | Status: VERIFYING%n",
//                    Thread.currentThread().getName().toUpperCase(),
//                    proxy.getIp() + ":" + proxy.getPort()
//            );



            System.out.println(ANSI_BLUE + "[TEST]     " + Thread.currentThread().getName().toUpperCase() + " | Connectivity check on: " + proxy.getIp() + ANSI_RESET);
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
//            System.out.println(proxy.toString().toUpperCase()+" connected and returned status code "+response.statusCode());

            if(response.statusCode() == 200){

                System.out.printf(ANSI_GREEN + "[SUCCESS]  %-15s | Proxy: %-15s | Status: %d | OK%s%n",
                        Thread.currentThread().getName().toUpperCase(),
                        proxy.getIp(),
                        response.statusCode(),
                        ANSI_RESET// This stops the green from "bleeding" into the next line
                );
                return true;
            }
            else {
                System.out.printf(ANSI_RED + "[FAILURE]  %-15s | Proxy: %-15s | Status: %d | OK%n",
                        Thread.currentThread().getName().toUpperCase(),
                        proxy.getIp(),
                        response.statusCode()
                );
                return false;
            }
        } catch (Exception e) {
            System.out.printf("%s[FAILURE]   %-15s | Proxy: %-15s | Error: %s%s%n",
                    LogColors.RED,
                    Thread.currentThread().getName().toUpperCase(),
                    proxy.getIp(),
                    e.getMessage(),
                    LogColors.RESET
            );        }
        return false;
    }

    public Proxy getProxy() throws IOException {
        if(workingProxy.isEmpty()){
//            System.out.println("There is no working proxies currently available");
            System.out.printf("[EMPTY_POOL] %-15s | Message: NO PROXIES AVAILABLE | Action: RETRYING%n",
                    Thread.currentThread().getName().toUpperCase()
            );
            return null;
        }
        return workingProxy.poll();
    }

    public void addWorkingProxy(Proxy proxy){
        workingProxy.add(proxy);
    }

    public void addUntesetedProxy(Proxy proxy){
        unTestedProxy.add(proxy);
    }

    public void startProxyService(){
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        loadProxy();
        startHealthChecker();
    }

    public void stopHealthChecker() throws InterruptedException {
        System.out.printf("%n%s[SHUTDOWN]  %-15s | Signal sent to Proxy HealthChecker.%s%n",
                LogColors.PURPLE, "CORE", LogColors.RESET);
        healthChecker.shutdown();
        try {
            System.out.printf("%s[SHUTDOWN]  %-15s | Waiting 1s for threads to exit gracefully...%s%n",
                    LogColors.PURPLE, "CORE", LogColors.RESET);
            if (!healthChecker.awaitTermination(10, TimeUnit.SECONDS)) {
                // 3. Force shutdown if they didn't finish in time
                System.out.printf("%s[SHUTDOWN]  %-15s | Time limit reached. Forcing immediate stop!%s%n",
                        LogColors.RED, "CORE", LogColors.RESET);
                healthChecker.shutdownNow();

                // 4. Wait a bit more for tasks to respond to the interrupt
                if (!healthChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("[CRITICAL] Proxy HealthChecker did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            System.out.printf("%s[SHUTDOWN]  %-15s | Interrupted during wait. Forcing shutdown.%s%n",
                    LogColors.RED, "CORE", LogColors.RESET);
            healthChecker.shutdownNow();
        }

    }


}



