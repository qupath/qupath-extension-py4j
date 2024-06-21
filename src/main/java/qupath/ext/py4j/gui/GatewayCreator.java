package qupath.ext.py4j.gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.VBox;
import qupath.ext.py4j.core.GatewayManager;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * A form to start a {@link GatewayManager} that ask the user
 * for a port and an optional token.
 */
class GatewayCreator extends VBox {

    private static final ResourceBundle resources = UiUtils.getResources();
    @FXML
    private TextField port;
    @FXML
    private TextField token;
    @FXML
    private Button copy;

    /**
     * Create the form.
     *
     * @param port the default port to show
     * @throws IOException when an error occurs while creating the window
     */
    public GatewayCreator(int port) throws IOException {
        UiUtils.loadFXML(this, GatewayCreator.class.getResource("gateway_creator.fxml"));

        this.port.setPromptText(MessageFormat.format(resources.getString("GatewayCreator.defaultPort"), port));
        this.port.setTextFormatter(new TextFormatter<>(getPositiveIntegerFilter()));
        this.port.setText(String.valueOf(port));

        copy.disableProperty().bind(token.textProperty().isEmpty());
    }

    @FXML
    private void onRandomClicked(ActionEvent ignoredEvent) {
        token.setText(UUID.randomUUID().toString());
    }

    @FXML
    private void onCopyClicked(ActionEvent ignoredEvent) {
        Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, token.getText()));
        Dialogs.showInfoNotification(
                resources.getString("GatewayCreator.title"),
                resources.getString("GatewayCreator.tokenCopied")
        );
    }

    /**
     * @return the port indicated by the user, or -1 if not provided
     */
    public int getPort() {
        try {
            return Integer.parseInt(port.getText());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return the token indicated by the user
     */
    public String getToken() {
        return token.getText();
    }

    private UnaryOperator<TextFormatter.Change> getPositiveIntegerFilter() {
        Pattern unsignerIntegerPattern = Pattern.compile("\\d*");

        return change -> {
            if (unsignerIntegerPattern.matcher(change.getControlNewText()).matches()) {
                if (change.getControlNewText().isEmpty()) {
                    return change;
                } else {
                    try {
                        int value = Integer.parseInt(change.getControlNewText());
                        if (value < 0) {
                            return null;
                        } else {
                            return change;
                        }
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else {
                return null;
            }
        };
    }
}
