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
            System.out.println("No untestedProxy available");

//            System.out.println(workingProxy);
//            System.out.println(workingProxy.size());
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
        healthChecker.shutdown();
        try {
            if (!healthChecker.awaitTermination(10, TimeUnit.SECONDS)) {
                // 3. Force shutdown if they didn't finish in time
                healthChecker.shutdownNow();

                // 4. Wait a bit more for tasks to respond to the interrupt
                if (!healthChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


}



