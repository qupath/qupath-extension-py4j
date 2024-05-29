package qupath.ext.py4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

import java.util.function.Consumer;

/**
 * A listener to a {@link GatewayServer}
 */
class Py4JListener implements GatewayServerListener {

    private static final Logger logger = LoggerFactory.getLogger(Py4JListener.class);
    private final Consumer<Boolean> onRunningStateChanged;

    /**
     * Create the listener.
     *
     * @param onRunningStateChanged  a function that will be called each time the state of the
     *                               underlying {@link GatewayServer} changes. This function
     *                               may be called from any thread
     */
    public Py4JListener(Consumer<Boolean> onRunningStateChanged) {
        this.onRunningStateChanged = onRunningStateChanged;
    }

    @Override
    public void serverStarted() {
        logger.debug("Py4J server started");

        onRunningStateChanged.accept(true);
    }

    @Override
    public void connectionStarted(Py4JServerConnection gatewayConnection) {
        logger.info("Gateway connection started");
    }

    @Override
    public void connectionStopped(Py4JServerConnection gatewayConnection) {
        logger.info("Gateway connection stopped");
    }

    @Override
    public void serverPreShutdown() {}

    @Override
    public void serverStopped() {
        logger.debug("Py4J server stopped");
    }

    @Override
    public void serverPostShutdown() {
        logger.debug("Py4J server shutdown");

        onRunningStateChanged.accept(false);
    }

    @Override
    public void serverError(Exception e) {
        logger.error("Py4J server error", e);
    }

    @Override
    public void connectionError(Exception e) {
        logger.error("Py4J connection error", e);
    }
}
