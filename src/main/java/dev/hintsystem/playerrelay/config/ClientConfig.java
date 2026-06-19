package dev.hintsystem.playerrelay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hintsystem.playerrelay.gui.AnchorPoint;
import dev.hintsystem.playerrelay.gui.PlayerListEntry;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector2i;

import java.awt.*;

public class ClientConfig extends CommonConfig {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Color.class, new ColorTypeAdapter())
        .create();

    public static final ClientConfig DEFAULTS = new ClientConfig();

    public Color displayNameColor = PlayerBasicData.DEFAULT_NAME_COLOR;
    public int afkTimeout = 2 * 60 * 1000;

    public boolean showPlayerList = true;
    public boolean useResourcePackIcons = false;
    public int playerListMaxPlayers = 8;
    public boolean showPlayerListDimensionIcon = true;
    public PlayerListEntry.PlayerIconType playerListIconType = PlayerListEntry.PlayerIconType.PLAYER_MODEL;
    public AnchorPoint playerListAnchorPoint = AnchorPoint.TOP_RIGHT;
    public Vector2i playerListOffset = new Vector2i(5, 5);
    public int playerListInfoWidth = 86;
    public Color playerListbackgroundColor = new Color(0, 0, 0, 60);

    public boolean shareWaypointsViaRelay = true;
    public boolean showTrackedPlayers = true;
    public boolean showTrackedPlayersFromOtherServers = false;

    public boolean showPingsFromOtherServers = false;

    @Override
    public ClientConfig getDefaults() { return DEFAULTS; }

    @Override
    protected Gson getGson() { return GSON; }

    public Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
            .title(Text.literal("PlayerRelayClient Config"))

            .category(ConfigCategory.createBuilder()
                .name(Text.literal("General"))

                .group(OptionGroup.createBuilder()
                    .name(Text.literal("Host"))
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Auto Host"))
                        .description(OptionDescription.of(Text.literal("Automatically start hosting a relay when the game launches.\n\n")
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Enabled").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal(" → Relay starts at game startup\n"))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Disabled").formatted(Formatting.RED))
                            .append(Text.literal(" → Relay must be started manually"))
                        ))
                        .binding(DEFAULTS.autoHost, () -> autoHost, val -> autoHost = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("UPnP Enabled"))
                        .description(OptionDescription.of(Text.literal("Automatically configure port forwarding using UPnP.\n\n")
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Enabled").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal(" → Automatically opens the hosting port on your router\n"))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Disabled").formatted(Formatting.RED))
                            .append(Text.literal(" → You must manually forward the port\n\n"))
                            .append(Text.literal("⚠ Requires a UPnP-capable router").formatted(Formatting.GOLD))
                        ))
                        .binding(DEFAULTS.UPnPEnabled, () -> UPnPEnabled, val -> UPnPEnabled = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<String>createBuilder()
                        .name(Text.literal("Connection Address"))
                        .description(OptionDescription.of(Text.literal("The address used when copying the connect command.\n\n")
                            .append(Text.literal("• Any string ").formatted(Formatting.GRAY))
                            .append(Text.literal("→ Uses this string directly\n\n"))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("external").formatted(Formatting.YELLOW, Formatting.BOLD))
                            .append(Text.literal(" → Uses your external IP\n"))
                            .append(Text.literal("default, ")
                                .append(Text.literal("requires UPnP\n\n").formatted(Formatting.BOLD))
                                .formatted(Formatting.GRAY))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("local").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal(" → Uses your local IP"))
                        ))
                        .binding(DEFAULTS.connectionAddress, () -> connectionAddress, val -> connectionAddress = val)
                        .controller(StringControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Use Encrypted Join Codes"))
                        .description(OptionDescription.of(Text.literal("When copying the connect command the Connection Address will be encrypted and displayed as a join code.\n\n")
                            .append(Text.empty()
                                .append(Text.literal("⚠ Warning!\n").formatted(Formatting.BOLD))
                                .append(Text.literal(
                                """
                                This will not hide your ip address from people connecting to your relay.
                                If you want to stay hidden you need to use a proxy or VPN.
                                """
                                )).formatted(Formatting.RED))
                        ))
                        .binding(DEFAULTS.useJoinCodes, () -> useJoinCodes, val -> useJoinCodes = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Default Hosting Port"))
                        .description(OptionDescription.of(Text.literal(
                            """
                            The default port for hosting connections.
                            
                            If this port cannot be mapped via UPnP, the next available port will be chosen.
                            """
                        )))
                        .binding(DEFAULTS.defaultHostingPort, () -> defaultHostingPort, val -> defaultHostingPort = val)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                            .formatValue(val -> Text.literal(String.format("%d", val)))
                            .range(1, 65535))
                        .build())
                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Text.literal("Client"))
//                    .option(Option.<String>createBuilder()
//                        .name(Text.literal("Auto Connect Address"))
//                        .binding(DEFAULTS.autoConnectAddress, () -> autoConnectAddress, val -> autoConnectAddress = val)
//                        .controller(StringControllerBuilder::create)
//                        .build())
                    .option(Option.<Color>createBuilder()
                        .name(Text.literal("Display Name Color"))
                        .binding(DEFAULTS.displayNameColor, () -> displayNameColor, val -> displayNameColor = val)
                        .controller(opt -> ColorControllerBuilder.create(opt)
                            .allowAlpha(true)) // TODO: set allowAlpha to false when YACL fixes the crash caused by ColorPickerWidget
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("AFK Timeout"))
                        .description(OptionDescription.of(Text.literal(
                            "Time (in ms) before you are marked as AFK after no keyboard or mouse input."
                        )))
                        .binding(DEFAULTS.afkTimeout, () -> afkTimeout, val -> afkTimeout = val)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                            .range(4000, 30 * 60 * 1000))
                        .build())
                    .option(Option.<Double>createBuilder()
                        .name(Text.literal("Minimum Player Movement"))
                        .description(OptionDescription.of(Text.literal(
                            """
                            Minimum distance (in blocks) a player must move before broadcasting a new position.
                            
                            Prevents unnecessary network updates for tiny movements.
                            """
                        )))
                        .binding(DEFAULTS.minPlayerMove, () -> minPlayerMove, val -> minPlayerMove = val)
                        .controller(opt -> DoubleFieldControllerBuilder.create(opt)
                            .range(0.01, 1000.0))
                        .build())
                    .build())
                .build())

            .category(ConfigCategory.createBuilder()
                .name(Text.literal("User Interface"))

                .group(OptionGroup.createBuilder()
                    .name(Text.literal("Player List"))
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Show"))
                        .binding(DEFAULTS.showPlayerList, () -> showPlayerList, val -> showPlayerList = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Use Resource Pack Icons"))
                        .description(OptionDescription.of(Text.literal("If enabled, then instead of using vanilla icons, the current resource pack icons will be used")))
                        .binding(DEFAULTS.useResourcePackIcons, () -> useResourcePackIcons, val -> useResourcePackIcons = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Max Visible Players"))
                        .binding(DEFAULTS.playerListMaxPlayers, () -> playerListMaxPlayers, val -> playerListMaxPlayers = val)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                            .range(1, 20))
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Show Dimension Icon"))
                        .binding(DEFAULTS.showPlayerListDimensionIcon, () -> showPlayerListDimensionIcon, val -> showPlayerListDimensionIcon = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<PlayerListEntry.PlayerIconType>createBuilder()
                        .name(Text.literal("Player Icon Type"))
                        .binding(DEFAULTS.playerListIconType, () -> playerListIconType, val -> playerListIconType = val)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                            .enumClass(PlayerListEntry.PlayerIconType.class))
                        .build())
                    .option(Option.<AnchorPoint>createBuilder()
                        .name(Text.literal("Anchor Point"))
                        .binding(DEFAULTS.playerListAnchorPoint, () -> playerListAnchorPoint, val -> playerListAnchorPoint = val)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                            .enumClass(AnchorPoint.class))
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("X Offset"))
                        .binding(DEFAULTS.playerListOffset.x, () -> playerListOffset.x, val -> playerListOffset.x = val)
                        .controller(IntegerFieldControllerBuilder::create)
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Y Offset"))
                        .binding(DEFAULTS.playerListOffset.y, () -> playerListOffset.y, val -> playerListOffset.y = val)
                        .controller(IntegerFieldControllerBuilder::create)
                        .build())
                    .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Info Width"))
                        .binding(DEFAULTS.playerListInfoWidth, () -> playerListInfoWidth, val -> playerListInfoWidth = val)
                        .controller(IntegerFieldControllerBuilder::create)
                        .build())
                    .option(Option.<Color>createBuilder()
                        .name(Text.literal("Background Color"))
                        .binding(DEFAULTS.playerListbackgroundColor, () -> playerListbackgroundColor, val -> playerListbackgroundColor = val)
                        .controller(opt -> ColorControllerBuilder.create(opt)
                            .allowAlpha(true))
                        .build())
                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Text.literal("Xaero's Minimap / WorldMap"))
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Share Waypoints Via Player Relay"))
                        .description(OptionDescription.of(Text.literal("Share waypoints through the connected relays instead of public chat.")))
                        .binding(DEFAULTS.shareWaypointsViaRelay, () -> shareWaypointsViaRelay, val -> shareWaypointsViaRelay = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Show Players"))
                        .description(OptionDescription.of(Text.literal("Display connected relay players on Xaero's Minimap and World Map as tracked players.")))
                        .binding(DEFAULTS.showTrackedPlayers, () -> showTrackedPlayers, val -> showTrackedPlayers = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Show Players From Other Servers"))
                        .description(OptionDescription.of(Text.literal("Show relay players even when they're on different servers.\n\n")
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Enabled").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal(" → See all relay players across servers\n"))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Disabled").formatted(Formatting.RED))
                            .append(Text.literal(" → Only show players on your current server\n\n"))
                            .append(Text.literal("Note: ").formatted(Formatting.GRAY))
                            .append(Text.literal("Requires 'Show Players' to be enabled").formatted(Formatting.YELLOW))
                        ))
                        .binding(DEFAULTS.showTrackedPlayersFromOtherServers, () -> showTrackedPlayersFromOtherServers, val -> showTrackedPlayersFromOtherServers = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Text.literal("Ping Wheel"))
                    .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Show Pings From Other Servers"))
                        .description(OptionDescription.of(Text.literal("Display ping markers from relay players on different servers.\n\n")
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Enabled").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal(" → See pings across all servers\n"))
                            .append(Text.literal("• ").formatted(Formatting.GRAY))
                            .append(Text.literal("Disabled").formatted(Formatting.RED))
                            .append(Text.literal(" → Only see pings on your current server\n\n"))
                        ))
                        .binding(DEFAULTS.showPingsFromOtherServers, () -> showPingsFromOtherServers, val -> showPingsFromOtherServers = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .build())

            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Advanced"))

                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("Connection Timeout"))
                    .description(OptionDescription.of(Text.literal(
                        "Maximum time (in ms) to wait for a peer version response before disconnecting."
                    )))
                    .binding(DEFAULTS.peerConnectionTimeout, () -> peerConnectionTimeout, val -> peerConnectionTimeout = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(50, 100_000))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("TCP Send Interval"))
                    .description(OptionDescription.of(Text.literal(
                        """
                        Delay (in ms) between sending player updates via TCP.
                        
                        Larger values = fewer updates, less bandwidth.
                        """
                    )))
                    .binding(DEFAULTS.tcpSendIntervalMs, () -> tcpSendIntervalMs, val -> tcpSendIntervalMs = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(50, 10_000))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("UDP Send Interval"))
                    .description(OptionDescription.of(Text.literal(
                        "Delay (in ms) between sending player updates via UDP."
                    )))
                    .binding(DEFAULTS.udpSendIntervalMs, () -> udpSendIntervalMs, val -> udpSendIntervalMs = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(10, 10_000))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("UDP Ping Interval"))
                    .description(OptionDescription.of(Text.literal(
                        """
                        How often (in ms) to send a UDP ping to check connection health.
                        
                        Shorter intervals = faster detection, but more network traffic.
                        """
                    )))
                    .binding(DEFAULTS.udpPingIntervalMs, () -> udpPingIntervalMs, val -> udpPingIntervalMs = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(500, 50_000))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("UDP Ping Timeout"))
                    .description(OptionDescription.of(Text.literal(
                        "Maximum time (in ms) to wait for a UDP ping response before counting it as failed."
                    )))
                    .binding(DEFAULTS.udpPingTimeoutMs, () -> udpPingTimeoutMs, val -> udpPingTimeoutMs = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(100, 10_000))
                    .build())
                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("UDP Max Failed Pings"))
                    .description(OptionDescription.of(Text.literal(
                        "Number of failed UDP pings allowed before marking the connection as unhealthy and falling back to TCP."
                    )))
                    .binding(DEFAULTS.maxFailedUdpPings, () -> maxFailedUdpPings, val -> maxFailedUdpPings = val)
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(1, 100))
                    .build())
                .build())

            .save(this::serialize)
            .build()
            .generateScreen(parent);
    }
}
