package qupath.ext.py4j.core;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import py4j.GatewayServer;

/**
 * Start, stop, and manage the state of a {@link GatewayServer}.
 */
public class GatewayManager {

    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private GatewayServer server;

    /**
     * Start a new {@link GatewayServer} with the provided parameters. If a
     * {@link GatewayServer} is already running, it is stopped first.
     *
     * @param port the port the {@link GatewayServer} should use, or a negative number to use the default port
     * @param token the token the {@link GatewayServer} should accept
     */
    public void start(int port, String token) {
        stop();

        server = new GatewayServer.GatewayServerBuilder()
                .entryPoint(new QuPathEntryPoint())
                .javaPort(port > 0 ? port : GatewayServer.DEFAULT_PORT)
                .authToken(token == null || token.isBlank() ? null : token)
                .build();

        server.addListener(new Py4JListener(running -> {
            synchronized (this) {
                this.running.set(running);
            }
        }));
        server.start();
    }

    /**
     * Stop the currently running {@link GatewayServer}.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
            server = null;

            synchronized (this) {
                running.set(false);
            }
        }
    }

    /**
     * @return a property indicating if a {@link GatewayServer} is running. This
     * property may be updated from any thread
     */
    public ReadOnlyBooleanProperty isRunning() {
        return running;
    }
}
