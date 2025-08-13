package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.PlayerRelay;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;

import java.net.InetAddress;
import java.util.Map;

public class UPnPManager {
    private GatewayDevice gateway;
    private String localIP;

    public UPnPManager() throws Exception {
        discoverGateway();
    }

    private void discoverGateway() throws Exception {
        GatewayDiscover discover = new GatewayDiscover();
        PlayerRelay.LOGGER.info("Looking for UPnP gateway devices...");

        Map<InetAddress, GatewayDevice> gateways = discover.discover();

        if (gateways.isEmpty()) {
            throw new Exception("No UPnP gateway found");
        }

        gateway = discover.getValidGateway();
        if (gateway == null) {
            throw new Exception("No valid UPnP gateway found");
        }

        localIP = gateway.getLocalAddress().getHostAddress();
        PlayerRelay.LOGGER.info("Found UPnP gateway: {} at {}", gateway.getFriendlyName(), gateway.getPresentationURL());
        PlayerRelay.LOGGER.info("Local IP: {}", localIP);
    }

    public boolean openPort(int port, String protocol) {
        try {
            // Check if port is already mapped
            PortMappingEntry portMapping = new PortMappingEntry();
            if (gateway.getSpecificPortMappingEntry(port, protocol, portMapping)) {
                PlayerRelay.LOGGER.info("Port {} is already mapped to {}", port, portMapping.getInternalClient());
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
                PlayerRelay.LOGGER.info("Successfully mapped port {} ({}) to {}", port, protocol, localIP);
                return true;
            } else {
                PlayerRelay.LOGGER.error("Failed to map port {}", port);
                return false;
            }

        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Error mapping port {}: {}", port, e.getMessage());
            return false;
        }
    }

    public boolean closePort(int port, String protocol) {
        try {
            boolean success = gateway.deletePortMapping(port, protocol);
            if (success) {
                PlayerRelay.LOGGER.info("Successfully unmapped port {} ({})", port, protocol);
            } else {
                PlayerRelay.LOGGER.error("Failed to unmap port {}", port);
            }
            return success;
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Error unmapping port {}: {}", port, e.getMessage());
            return false;
        }
    }

    public String getExternalIp() {
        try {
            return gateway.getExternalIPAddress();
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to get external IP: {}", e.getMessage());
            return null;
        }
    }

    public String getLocalIp() {
        return localIP;
    }
}
