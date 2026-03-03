package com.goldsprite.gdengine.netcode.supabase;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;

/**
 * IP 地址工具类：获取公网 IP 和局域网 IP
 */
public class PublicIPResolver {

    /**
     * 获取本机局域网 IPv4 地址（排除 127.0.0.1）。
     * 优先返回非虚拟网卡的地址；找不到时返回 "127.0.0.1"。
     */
    public static String getLocalIP() {
        try {
            // 第一轮：找 siteLocal 且非虚拟、非回环的网卡 IP
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略，走 fallback
        }
        return "127.0.0.1";
    }
    
    private static final String API_URL = "https://checkip.amazonaws.com";
    
    public interface ResolveCallback {
        void onSuccess(String ip);
        void onError(Throwable t);
    }
    
    /**
     * 异步获取当前机器的公网 IPv4 地址
     */
    public static void resolvePublicIP(final ResolveCallback callback) {
        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();
        Net.HttpRequest httpRequest = requestBuilder.newRequest()
            .method(Net.HttpMethods.GET)
            .url(API_URL)
            .timeout(5000)
            .build();
            
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int statusCode = httpResponse.getStatus().getStatusCode();
                if (statusCode == 200) {
                    String ip = httpResponse.getResultAsString().trim();
                    if (callback != null) {
                        callback.onSuccess(ip);
                    }
                } else {
                    if (callback != null) {
                        callback.onError(new RuntimeException("获取公网IP失败，HTTP状态码: " + statusCode));
                    }
                }
            }

            @Override
            public void failed(Throwable t) {
                if (callback != null) {
                    callback.onError(t);
                }
            }
            
            @Override
            public void cancelled() {
                if (callback != null) {
                    callback.onError(new RuntimeException("获取公网IP请求被取消"));
                }
            }
        });
    }
}