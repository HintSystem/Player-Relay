package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.logging.LogEventTypes;
import dev.hintsystem.playerrelay.logging.PlayerRelayLogger;
import dev.hintsystem.playerrelay.logging.LogLocation;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;

import java.net.InetAddress;
import java.util.Map;

public class UPnPManager {
    public final PlayerRelayLogger logger;

    private GatewayDevice gateway;
    private String localIP;

    public UPnPManager() throws Exception { this(new PlayerRelayLogger(LogLocation.UPNP_MANAGER)); }

    public UPnPManager(PlayerRelayLogger logger) throws Exception {
        this.logger = logger.withLocation(LogLocation.UPNP_MANAGER);
        discoverGateway();
    }

    private void discoverGateway() throws Exception {
        GatewayDiscover discover = new GatewayDiscover();
        logger.info().message("Looking for UPnP gateway devices...").build();

        Map<InetAddress, GatewayDevice> gateways = discover.discover();

        if (gateways.isEmpty()) {
            throw new Exception("No UPnP gateway found");
        }

        gateway = discover.getValidGateway();
        if (gateway == null) {
            throw new Exception("No valid UPnP gateway found");
        }

        localIP = gateway.getLocalAddress().getHostAddress();

        logger.info().message("Found UPnP gateway: {} at {}", gateway.getFriendlyName(), gateway.getPresentationURL()).build();
    }

    public boolean openPort(int port, String protocol) {
        try {
            // Check if port is already mapped
            PortMappingEntry portMapping = new PortMappingEntry();
            if (gateway.getSpecificPortMappingEntry(port, protocol, portMapping)) {
                logger.info().message("Port {} is already mapped to {}", port, portMapping.getInternalClient()).build();
                return portMapping.getInternalClient().equals(localIP);
            }

            // Map the port
            boolean success = gateway.addPortMapping(
                    port,           // external port
                    port,           // internal port
                    localIP,        // internal client
                    protocol,       // protocol
                    "Player Relay mod"       // description
            );

            if (success) {
                logger.info().message("Successfully mapped port {} ({}) to {}", port, protocol, localIP).build();
                return true;
            } else {
                logger.error()
                    .type(LogEventTypes.PORT_MAP_FAIL)
                    .title("Failed to map port {} ({})", port, protocol).build();
                return false;
            }

        } catch (Exception e) {
            logger.error()
                .type(LogEventTypes.PORT_MAP_FAIL)
                .title("Error mapping port {} ({})", port, protocol)
                .exception(e).build();
            return false;
        }
    }

    public boolean closePort(int port, String protocol) {
        try {
            boolean success = gateway.deletePortMapping(port, protocol);
            if (success) {
                logger.info().message("Successfully unmapped port {} ({})", port, protocol).build();
            } else {
                logger.error().message("Failed to unmap port {}", port).build();
            }
            return success;
        } catch (Exception e) {
            logger.error().message("Error unmapping port {}: {}", port, e.getMessage()).build();
            return false;
        }
    }

    public String getExternalIp() {
        try {
            return gateway.getExternalIPAddress();
        } catch (Exception e) {
            logger.error().message("Failed to get external IP: {}", e.getMessage()).build();
            return null;
        }
    }

    public String getLocalIp() { return localIP; }
}
