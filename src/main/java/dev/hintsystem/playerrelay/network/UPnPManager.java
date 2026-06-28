package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.network.logging.LogEventTypes;
import dev.hintsystem.playerrelay.network.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.logging.LogEventLocation;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;

import java.net.InetAddress;
import java.util.Map;

public class UPnPManager {
    public final NetworkLogger logger;

    private GatewayDevice gateway;
    private String localIP;

    public UPnPManager(NetworkLogger logger) throws Exception {
        this.logger = logger.withLocation(LogEventLocation.UPNP_MANAGER);
        discoverGateway();
    }

    private void discoverGateway() throws Exception {
        GatewayDiscover discover = new GatewayDiscover();
        logger.builder()
            .message("Looking for UPnP gateway devices...")
            .info();

        Map<InetAddress, GatewayDevice> gateways = discover.discover();

        if (gateways.isEmpty()) {
            throw new Exception("No UPnP gateway found");
        }

        gateway = discover.getValidGateway();
        if (gateway == null) {
            throw new Exception("No valid UPnP gateway found");
        }

        localIP = gateway.getLocalAddress().getHostAddress();

        logger.builder()
            .message("Found UPnP gateway: {} at {}", gateway.getFriendlyName(), gateway.getPresentationURL())
            .info();
    }

    public boolean openPort(int port, String protocol) {
        try {
            // Check if port is already mapped
            PortMappingEntry portMapping = new PortMappingEntry();
            if (gateway.getSpecificPortMappingEntry(port, protocol, portMapping)) {
                return portMapping.getInternalClient().equals(localIP);
            }

            // Map the port
            boolean success = gateway.addPortMapping(port, port, localIP, protocol, "Player Relay mod");

            if (success) {
                logger.builder()
                    .message("Successfully mapped port {} ({}) to {}", port, protocol, localIP)
                    .info();
                return true;
            } else {
                logger.builder()
                    .type(LogEventTypes.PORT_MAP_FAIL)
                    .title("Failed to map port {} ({})", port, protocol).error();
                return false;
            }

        } catch (Exception e) {
            logger.builder()
                .type(LogEventTypes.PORT_MAP_FAIL)
                .title("Error mapping port {} ({})", port, protocol)
                .cause(e).error();
            return false;
        }
    }

    public boolean closePort(int port, String protocol) {
        try {
            boolean success = gateway.deletePortMapping(port, protocol);
            if (success) {
                logger.builder()
                    .message("Successfully unmapped port {} ({})", port, protocol)
                    .info();
            } else {
                logger.builder()
                    .title("Failed to unmap port {} ({})", port, protocol)
                    .error();
            }
            return success;
        } catch (Exception e) {
            logger.builder()
                .title("Error unmapping port {} ({})", port, protocol)
                .cause(e).error();
            return false;
        }
    }

    public String getExternalIp() {
        try {
            return gateway.getExternalIPAddress();
        } catch (Exception e) {
            logger.builder()
                .title("Failed to get external IP")
                .cause(e).error();
            return null;
        }
    }

    public String getLocalIp() { return localIP; }
}
