package io.anyone.anyonebot.service;

import androidx.annotation.NonNull;

import io.anyone.anyonebot.service.util.Prefs;
import io.anyone.anyonebot.service.util.Utils;
import io.anyone.jni.AnonControlCommands;
import io.anyone.jni.AnonService;
import io.anyone.jni.RawEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class AnyoneBotRawEventListener implements RawEventListener {
    private final AnyoneBotService mService;
    private long mTotalBandwidthWritten, mTotalBandwidthRead;
    private final Map<String, DebugLoggingNode> hmBuiltNodes;
    private final Map<Integer, ExitNode> exitNodeMap;
    private final Set<Integer> ignoredInternalCircuits;

    private static final String CIRCUIT_BUILD_FLAG_IS_INTERNAL = "IS_INTERNAL";
    private static final String CIRCUIT_BUILD_FLAG_ONE_HOP_TUNNEL = "ONEHOP_TUNNEL";

    AnyoneBotRawEventListener(AnyoneBotService service) {
        mService = service;
        mTotalBandwidthRead = 0;
        mTotalBandwidthWritten = 0;
        hmBuiltNodes = new HashMap<>();

        exitNodeMap = new HashMap<>();
        ignoredInternalCircuits = new HashSet<>();

    }

    @Override
    public void onEvent(String keyword, String data) {
        String[] payload = data.split(" ");
        if (AnonControlCommands.EVENT_BANDWIDTH_USED.equals(keyword)) {
            handleBandwidth(Long.parseLong(payload[0]), Long.parseLong(payload[1]));
        } else if (AnonControlCommands.EVENT_NEW_DESC.equals(keyword)) {
            handleNewDescriptors(payload);
        } else if (AnonControlCommands.EVENT_STREAM_STATUS.equals(keyword)) {

            handleStreamEventExpandedNotifications(payload[1], payload[3], payload[2], payload[4]);

            if (Prefs.useDebugLogging()) handleStreamEventsDebugLogging(payload[1], payload[0]);
        } else if (AnonControlCommands.EVENT_CIRCUIT_STATUS.equals(keyword)) {
            String status = payload[1];
            String circuitId = payload[0];
            String path;
            if (payload.length < 3 || status.equals(AnonControlCommands.CIRC_EVENT_LAUNCHED))
                path = "";
            else path = payload[2];
            handleCircuitStatus(status, circuitId, path);

            // don't bother looking up internal circuits that Orbot clients won't directly use
            if (data.contains(CIRCUIT_BUILD_FLAG_ONE_HOP_TUNNEL) || data.contains(CIRCUIT_BUILD_FLAG_IS_INTERNAL)) {
                ignoredInternalCircuits.add(Integer.parseInt(circuitId));
            }
            handleCircuitStatusExpandedNotifications(status, circuitId, path);

        } else if (AnonControlCommands.EVENT_OR_CONN_STATUS.equals(keyword)) {
            handleConnectionStatus(payload[1], payload[0]);
        } else if (AnonControlCommands.EVENT_DEBUG_MSG.equals(keyword) || AnonControlCommands.EVENT_INFO_MSG.equals(keyword) || AnonControlCommands.EVENT_NOTICE_MSG.equals(keyword) || AnonControlCommands.EVENT_WARN_MSG.equals(keyword) || AnonControlCommands.EVENT_ERR_MSG.equals(keyword)) {
            handleDebugMessage(keyword, data);
        } else {
            String unrecognized = "Message (" + keyword + "): " + data;
            mService.logNotice(unrecognized);
        }
    }

    private void handleBandwidth(long read, long written) {
        String message = AnyoneBotService.formatBandwidthCount(mService, read) + " ↓ / " + AnyoneBotService.formatBandwidthCount(mService, written) + " ↑";

        if (mService.getCurrentStatus().equals(AnonService.STATUS_ON))
            mService.showBandwidthNotification(message, read != 0 || written != 0);

        mTotalBandwidthWritten += written;
        mTotalBandwidthRead += read;

        mService.sendCallbackBandwidth(written, read, mTotalBandwidthWritten, mTotalBandwidthRead);

    }

    private void handleNewDescriptors(String[] descriptors) {
        for (String descriptor : descriptors)
            mService.debug("descriptors: " + descriptor);
    }

    private void handleStreamEventExpandedNotifications(String status, String target, String circuitId, String clientProtocol) {
        if (!status.equals(AnonControlCommands.STREAM_EVENT_SUCCEEDED)) return;
        if (!clientProtocol.contains("SOCKS5")) return;
        int id = Integer.parseInt(circuitId);
        if (target.contains(".onion"))
            return; // don't display to users exit node info for onion addresses!
        ExitNode node = exitNodeMap.get(id);
        if (node != null) {
            if (node.country == null && !node.querying) {
                node.querying = true;
                mService.exec(() -> {
                    try {
                        String[] networkStatus = mService.conn.getInfo("ns/id/" + node.fingerPrint).split(" ");
                        node.ipAddress = networkStatus[6];
                        String countryCode = mService.conn.getInfo("ip-to-country/" + node.ipAddress).toUpperCase(Locale.getDefault());
                        if (!countryCode.equals(TOR_CONTROLLER_COUNTRY_CODE_UNKNOWN)) {
                            String emoji = Utils.convertCountryCodeToFlagEmoji(countryCode);
                            String countryName = new Locale("", countryCode).getDisplayName();
                            node.country = emoji + " " + countryName;
                        } else node.country = "";
                        mService.setNotificationSubtext(node.toString());
                    } catch (Exception ignored) {
                    }
                });
            } else {
                if (node.country != null) mService.setNotificationSubtext(node.toString());
                else mService.setNotificationSubtext(null);
            }
        }
    }

    private static final String TOR_CONTROLLER_COUNTRY_CODE_UNKNOWN = "??";

    private void handleStreamEventsDebugLogging(String streamId, String status) {
        mService.debug("StreamStatus (" + streamId + "): " + status);
    }

    private void handleCircuitStatusExpandedNotifications(String circuitStatus, String circuitId, String path) {
        int id = Integer.parseInt(circuitId);
        switch (circuitStatus) {
            case AnonControlCommands.CIRC_EVENT_BUILT -> {
                if (ignoredInternalCircuits.contains(id))
                    return; // this circuit won't be used by user clients
                String[] nodes = path.split(",");
                String exit = nodes[nodes.length - 1];
                String fingerprint = exit.split("~")[0];
                exitNodeMap.put(id, new ExitNode(fingerprint));
            }
            case AnonControlCommands.CIRC_EVENT_CLOSED -> {
                exitNodeMap.remove(id);
                ignoredInternalCircuits.remove(id);
            }
            case AnonControlCommands.CIRC_EVENT_FAILED -> ignoredInternalCircuits.remove(id);
        }
    }

    private void handleCircuitStatus(String circuitStatus, String circuitId, String path) {
        if (!Prefs.useDebugLogging()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Circuit (");
        sb.append((circuitId));
        sb.append(") ");
        sb.append(circuitStatus);
        sb.append(": ");

        StringTokenizer st = new StringTokenizer(path, ",");
        DebugLoggingNode node;

        boolean isFirstNode = true;
        int nodeCount = st.countTokens();

        while (st.hasMoreTokens()) {
            String nodePath = st.nextToken();
            String nodeId = null, nodeName = null;

            String[] nodeParts;

            if (nodePath.contains("=")) nodeParts = nodePath.split("=");
            else nodeParts = nodePath.split("~");

            if (nodeParts.length == 1) {
                nodeId = nodeParts[0].substring(1);
                nodeName = nodeId;
            } else if (nodeParts.length == 2) {
                nodeId = nodeParts[0].substring(1);
                nodeName = nodeParts[1];
            }

            if (nodeId == null) continue;

            node = hmBuiltNodes.get(nodeId);

            if (node == null) {
                node = new DebugLoggingNode();
                node.id = nodeId;
                node.name = nodeName;
            }

            node.status = circuitStatus;

            sb.append(node.name);

            if (st.hasMoreTokens()) sb.append(" > ");

            if (circuitStatus.equals(AnonControlCommands.CIRC_EVENT_EXTENDED) && isFirstNode) {
                hmBuiltNodes.put(node.id, node);
                isFirstNode = false;
            } else if (circuitStatus.equals(AnonControlCommands.CIRC_EVENT_LAUNCHED)) {
                if (Prefs.useDebugLogging() && nodeCount > 3) mService.debug(sb.toString());
            } else if (circuitStatus.equals(AnonControlCommands.CIRC_EVENT_CLOSED)) {
                hmBuiltNodes.remove(node.id);
            }

        }
    }

    private void handleConnectionStatus(String status, String unparsedNodeName) {
        String message = "orConnStatus (" + parseNodeName(unparsedNodeName) + "): " + status;
        mService.debug(message);
    }

    private void handleDebugMessage(String severity, String message) {
        if (severity.equalsIgnoreCase("debug")) mService.debug(severity + ": " + message);
        else mService.logNotice(severity + ": " + message);
    }

    public Map<String, DebugLoggingNode> getNodes() {
        return hmBuiltNodes;
    }

    /**
     * Used to store metadata about an exit node if expanded notifications are turned on
     */
    public static class ExitNode {
        ExitNode(String fingerPrint) {
            this.fingerPrint = fingerPrint;
        }

        public final String fingerPrint;
        public String country;
        public String ipAddress;
        boolean querying = false;

        @NonNull
        @Override
        public String toString() {
            return ipAddress + " " + country;
        }
    }


    public static class DebugLoggingNode {
        public String status;
        public String id;
        public String name;
    }


    private static String parseNodeName(String node) {
        if (node.indexOf('=') != -1) {
            return node.substring(node.indexOf("=") + 1);
        } else if (node.indexOf('~') != -1) {
            return node.substring(node.indexOf("~") + 1);
        }
        return node;
    }
}
