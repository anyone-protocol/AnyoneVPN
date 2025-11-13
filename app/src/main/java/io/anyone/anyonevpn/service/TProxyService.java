package io.anyone.anyonevpn.service;

public class TProxyService {

    public static final String VIRTUAL_GATEWAY_IPV4 = "198.18.0.1";
    public static final String VIRTUAL_GATEWAY_IPV6 = "fc00::1";
    public static final String FAKE_DNS = "198.18.0.2";

    public static final int TASK_SIZE = 81920;
    public static final int TUNNEL_MTU = 8500;

    public static native void TProxyStartService(String config_path, int fd);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
}
