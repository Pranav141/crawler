package com.pranav;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;

public class ProxyService {
    public List<Proxy> proxyList;
    public int counter;
    public void loadProxy() throws NullPointerException,IOException {
        try{
            InputStream file =  ProxyService.class.getClassLoader().getResourceAsStream("all-proxies.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
            List<String> proxies = bufferedReader.lines().toList();
            System.out.println(proxies);
            proxyList = new ArrayList<>();
            for (int i = 0; i < proxies.size(); i++) {
                String[] splits = proxies.get(i).split(":");
                String protocal = splits[0];
                String ip = splits[1].substring(2);
                Integer port = Integer.parseInt(splits[2]);
                Proxy proxy = new Proxy(ip,port,protocal);
                proxyList.add(proxy);
//                System.out.println(splits[i]+" "+i);
            }

        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }

    }
    public Proxy getProxy(){
        if(counter == proxyList.size()-1){
            counter = 0;
        }
        return proxyList.get(counter++);
    }
    static void main() throws IOException {
        ProxyService proxyService = new ProxyService();
        proxyService.loadProxy();

        System.out.println(proxyService.proxyList);
    }

}
