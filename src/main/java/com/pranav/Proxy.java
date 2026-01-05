package com.pranav;

public class Proxy {
    public String ip;
    public Integer port;
    public String username;
    public String password;

    public Proxy(String ip, Integer port, String username, String password) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Proxy{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                '}';
    }

    public String getIp() {
        return ip;
    }

    public Integer getPort() {
        return port;
    }


    @Override
    public boolean equals(Object obj) {
        Proxy obj1 = (Proxy) obj;
        if(this.ip.equals(obj1.ip) && this.port == obj1.port){
            return true;
        }
        return false;
    }
}
