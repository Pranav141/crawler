package com.pranav;



import java.net.*;
import java.util.ArrayList;
import java.util.List;

class ValidIp{
    public String ip;
    public String port;

    public ValidIp(String ip, String port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public String toString() {
        return "ValidIp{" +
                "ip='" + ip + '\'' +
                ", port='" + port + '\'' +
                '}';
    }
}
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {
        // 1. Define the proxy host and port
        List<ValidIp> validIps = new ArrayList<>();
        String[] ips = new String[]{"78.12.143.148", "40.192.3.100", "13.212.81.49", "43.217.158.81", "15.160.134.84", "43.205.124.165", "18.179.12.21", "54.90.159.174", "35.152.252.253", "13.218.86.1", "16.78.104.244", "78.12.230.52", "44.251.173.250", "40.177.211.224", "157.175.170.170", "78.12.230.52", "78.12.230.52", "157.175.43.137", "43.217.158.81", "35.152.239.124", "15.160.125.231", "108.137.1.116", "44.251.173.250", "78.12.230.52", "43.217.158.81", "43.217.158.81", "43.208.16.9", "35.152.252.253", "78.12.249.123", "40.192.3.100", "78.12.249.123", "34.236.148.220", "43.205.124.165", "108.137.1.116", "35.152.252.253", "35.180.127.14", "13.115.193.75", "78.12.230.52", "78.12.230.52", "13.232.2.142", "78.12.230.52", "72.10.160.170", "72.10.160.170", "61.76.95.217", "182.50.65.169", "109.199.125.66"};
        String[] ports = new String[]{"9165", "8770", "957", "48218", "8711", "11746", "8327", "52929", "1508", "8711", "52959", "32014", "46933", "59394", "799", "52643", "8111", "9392", "5741", "533", "627", "17277", "15280", "268", "20686", "3526", "59521", "10810", "46432", "8019", "8019", "8007", "8445", "26215", "8801", "34689", "4916", "17002", "22062", "20161", "8701", "4105", "19663", "40088", "8080", "3128"};
        for (int i = 0; i < ips.length; i++) {
            Proxy proxy = new Proxy(
                    Proxy.Type.SOCKS,
                    new InetSocketAddress(ips[i], Integer.parseInt(ports[i])) // proxy IP & port
            );

            URL url = new URL("https://en.wikipedia.org/wiki/World_War_I");
            try {
//                Socket socket = url.openConnection(proxy);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MyCrawler/1.0");

                int status = conn.getResponseCode();
                System.out.println("Status: " + status);
                validIps.add(new ValidIp(ips[i],ports[i]));
            } catch (Exception e) {
                System.out.println("couldnt connect"+ ips[i]);
                System.out.println(e.getMessage());
            }

        }
        System.out.println(validIps);
    }
}
