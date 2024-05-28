package qupath.ext.py4j;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class Gateway {

    private final BooleanProperty running = new SimpleBooleanProperty(false);

    public void run() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public ReadOnlyBooleanProperty isRunning() {
        return running;
    }
}
