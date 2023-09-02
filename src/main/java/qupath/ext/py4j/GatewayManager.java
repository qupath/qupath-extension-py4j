package qupath.ext.py4j;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

class GatewayManager {

    private static final Logger logger = LoggerFactory.getLogger(GatewayManager.class);

    private BooleanProperty gatewayRunning = new SimpleBooleanProperty(false);

    private ObjectProperty<GatewayServer> gatewayServer = new SimpleObjectProperty<>();

    private Py4JListener listener;

    GatewayManager() {
        gatewayServer.addListener(this::gatewayChanged);
    }

    ObjectProperty<GatewayServer> gatewayServerProperty() {
        return gatewayServer;
    }

    private void gatewayChanged(ObservableValue<? extends GatewayServer> observable, GatewayServer oldValue,
                                GatewayServer newValue) {
        if (oldValue != null) {
            if (oldValue.getGateway().isStarted())
                oldValue.shutdown();
            oldValue.removeListener(listener);
        }
        if (newValue != null) {
            if (listener == null)
                listener = new Py4JListener();
            newValue.addListener(listener);
            if (!newValue.getGateway().isStarted()) {
                newValue.start();
            }
        }
    }


    public void stopGateway() {
        var server = gatewayServer.get();
        if (server != null) {
            server.shutdown();
            server.removeListener(listener);
            gatewayServer.set(null);
        }
    }


    public boolean isGatewayRunning() {
        return gatewayRunning.get();
    }

    public ReadOnlyBooleanProperty gatewayRunningProperty() {
        return ReadOnlyBooleanProperty.readOnlyBooleanProperty(gatewayRunning);
    }


    private class Py4JListener implements GatewayServerListener {

        @Override
        public void connectionError(Exception e) {
            logger.error(e.getLocalizedMessage(), e);
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
        public void serverError(Exception e) {
            logger.error(e.getLocalizedMessage(), e);
        }

        @Override
        public void serverPostShutdown() {
            if (gatewayRunning.get()) {
                logger.info("Py4J gateway shutdown");
                gatewayRunning.set(false);
            }
        }

        @Override
        public void serverPreShutdown() {
        }

        @Override
        public void serverStarted() {
            logger.info("Py4J gateway started");
            gatewayRunning.set(true);
        }

        @Override
        public void serverStopped() {
            logger.debug("Py4J gateway stopped");
        }

    }

}
