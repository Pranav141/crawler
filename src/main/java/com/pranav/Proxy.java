package com.pranav;

public class Proxy {
    public String ip;
    public Integer port;
    public String protocol;

    public Proxy(String ip, Integer port, String protocol) {
        this.ip = ip;
        this.port = port;
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return "Proxy{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                ", protocol='" + protocol + '\'' +
                '}';
    }

    public String getIp() {
        return ip;
    }

    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }
}
