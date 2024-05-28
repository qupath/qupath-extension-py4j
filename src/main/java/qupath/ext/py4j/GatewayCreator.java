package qupath.ext.py4j;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.VBox;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

public class GatewayCreator extends VBox {

    private static final ResourceBundle resources = UiUtilities.getResources();
    @FXML
    private TextField port;
    @FXML
    private TextField token;
    @FXML
    private Button copy;

    public GatewayCreator(int port) throws IOException {
        UiUtilities.loadFXML(this, GatewayCreator.class.getResource("gateway_creator.fxml"));

        this.port.setPromptText(MessageFormat.format(resources.getString("GatewayCreator.defaultPort"), port));

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
}
