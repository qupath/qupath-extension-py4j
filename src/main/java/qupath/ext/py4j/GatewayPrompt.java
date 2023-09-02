package qupath.ext.py4j;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import py4j.GatewayServer;
import qupath.fx.dialogs.Dialogs;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

public class GatewayPrompt {

    private Integer port = null;
    private String token = "";

    @FXML
    private TextField tfPort;

    @FXML
    private TextField tfToken;

    @FXML
    private Button btnCopy;

    @FXML
    private ResourceBundle resources;

    @FXML
    private void initialize() {
        setPort(port == null ? "" : port.toString());
        setToken(token);
        btnCopy.disableProperty().bind(tfToken.textProperty().isEmpty());
    }

    @FXML
    public void createRandomToken() {
        tfToken.setText(UUID.randomUUID().toString());
    }

    @FXML
    public void copyToken() {
        String text = tfToken.getText();
        String title = resources.getString("py4j.title");
        if (text == null || text.isEmpty()) {
            Dialogs.showErrorNotification(title,
                    resources.getString("py4j.notifications.no_token"));
        } else {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, text));
            Dialogs.showInfoNotification(title,
                    resources.getString("py4j.notifications.token_copied"));
        }
    }

    public void setPort(String port) {
        if (port == null || port.isEmpty()) {
            String defaultPortString = Integer.toString(GatewayServer.DEFAULT_PORT);
            String defaultPrompt = String.format(
                    resources.getString("py4j.gateway.port.prompt")
                    , defaultPortString);
            tfPort.setText("");
            tfPort.setPromptText(defaultPrompt);
        } else {
            tfPort.setText(port);
        }
    }

    public void setToken(String token) {
        tfToken.setText(token);
    }

    public String getPort() {
        return tfPort == null ? "" : tfPort.getText();
    }

    public String getToken() {
        return tfToken == null ? "" : tfToken.getText();
    }

}
