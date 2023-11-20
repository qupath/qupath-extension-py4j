/*-
 * Copyright 2022 QuPath developers,  University of Edinburgh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.ext.py4j;

import java.util.Map;
import java.util.UUID;

import org.controlsfx.control.action.Action;
import org.kordamp.ikonli.javafx.StackedFontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import py4j.GatewayServer;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;


/**
 * QuPath extension to enable Python and Java/QuPath to communicate, via Py4J (http://py4j.org).
 * 
 * @author Pete Bankhead
 */
public class QuPathPy4JExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPathPy4JExtension.class);

	private boolean isInstalled = false;
	
	private BooleanProperty enablePy4J = PathPrefs.createPersistentPreference("enablePy4J", true);
	
	private Py4JCommand command = new Py4JCommand();
	
	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
				
		isInstalled = true;
				
		qupath.getPreferencePane().addPropertyPreference(
				enablePy4J,
				Boolean.class,
				"Enable Py4J",
				"Py4J",
				"Enable Py4J through the UI to accept connections - "
				+ "this enables QuPath to communicate with Python.\n"
				+ "See www.py4j.org for more info.");
				
		
		var action = createGatewayAction();

		var mi = ActionTools.createMenuItem(action);
		mi.graphicProperty().unbind();
		mi.setGraphic(createGatewayIcon(command.gatewayRunningProperty()));
		mi.visibleProperty().bind(enablePy4J);
		
		var btn = ActionTools.createButton(action);
		btn.textProperty().unbind();
		btn.graphicProperty().unbind();
		btn.getStyleClass().add("toggle-button");
		command.gatewayRunningProperty().addListener((v, o, n) -> {
			btn.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), n);
			if (n)
				Dialogs.showInfoNotification("Py4J", "Py4J Gateway started");
			else
				Dialogs.showInfoNotification("Py4J", "Py4J Gateway stopped");
		});
		btn.visibleProperty().bind(enablePy4J);
		
		btn.setGraphic(createGatewayIcon(command.gatewayRunningProperty()));
		btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		
		qupath.getToolBar().getItems().add(btn);
		
		var menu = qupath.getMenu("Extensions>Py4J", true);
		menu.visibleProperty().bind(enablePy4J);
		menu.getItems().add(mi);
	}
	
	
	private Action createGatewayAction() {
		var action = new Action("Py4J", e -> {
			if (command.isGatewayRunning())
				command.stopGateway();
			else
				promptToStartGateway();
		});
		action.textProperty().bind(Bindings.createStringBinding(() -> {
			if (action.isSelected())
				return "Stop Py4J Gateway";
			else
				return "Start Py4J Gateway";
		}, action.selectedProperty()));
		
		action.setLongText("Start a Py4J Gateway to communicate between QuPath and Python");		
		return action;
	}
	
	
	private Integer port = GatewayServer.DEFAULT_PORT;
	private String token = "";
	
	
	private boolean promptToStartGateway() {
		
		if (command.isGatewayRunning()) {
			logger.warn("Can't start new GatewayServer - please shut down the existing server first");
			return false;
		}
		
		var title = "Py4J Gateway";
		
		var pane = new GridPane();
		pane.setHgap(5);
		pane.setVgap(5);
		
		var labelPort = new Label("Port");
		var tfPort = new TextField();
		tfPort.setPromptText("Default port (" + port + ")");
		
		int row = 0;
		
		var labelInstructions = new Label("Create a Py4J Gateway so Python programs can \n"
				+ "access QuPath through a local network socket.\n"
				+ "Check out py4j.org for more details & security info.\n ");
		labelInstructions.setTextAlignment(TextAlignment.CENTER);
		labelInstructions.setAlignment(Pos.CENTER);
		PaneTools.setToExpandGridPaneWidth(labelInstructions);
		
		PaneTools.addGridRow(pane, row++, 0, null, labelInstructions, labelInstructions);

		
		PaneTools.addGridRow(pane, row++, 0, "Port number (integer), or leave blank to use the default Py4J port",
				labelPort, tfPort);
				
		var labelAuthToken = new Label("Token");
		var tfAuthToken = new TextField(token);
		tfAuthToken.setPromptText("Authentication token (optional)");
		tfAuthToken.setPrefColumnCount(24);
		var btnRandomToken = new Button("Random");
		btnRandomToken.setOnAction(e -> tfAuthToken.setText(UUID.randomUUID().toString()));
		btnRandomToken.setTooltip(new Tooltip("Generate a UUID to use as an authentication token"));
		
		var btnCopyToken = new Button("Copy");
		btnCopyToken.disableProperty().bind(tfAuthToken.textProperty().isEmpty());
		btnCopyToken.setOnAction(e -> {
			Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, tfAuthToken.getText()));
			Dialogs.showInfoNotification(title, "Token copied to clipboard!");
		});
		btnCopyToken.setTooltip(new Tooltip("Copy authentication token to clipboard (to use in Python)"));

		PaneTools.addGridRow(pane, row++, 0, "Authentication token - if provided, this needs to be entered on the Python side",
				labelAuthToken, tfAuthToken);
		var paneButtons = PaneTools.createColumnGrid(btnRandomToken, btnCopyToken);
		paneButtons.setHgap(5);
		PaneTools.setMaxWidth(Double.MAX_VALUE, btnRandomToken, btnCopyToken);
		PaneTools.addGridRow(pane, row++, 0, null, null, paneButtons, paneButtons);
		
		var result = Dialogs.builder()
				.title(title)
				.content(pane)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;
		
		if (!result)
			return false;

		var builder = new GatewayServer.GatewayServerBuilder()
				.entryPoint(new QuPathEntryPoint());
		
		var portText = tfPort.getText();
		if (portText != null && !portText.isBlank()) {
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException e) {
				Dialogs.showErrorMessage(title, "Unable to parse port number - must be a valid integer");
				return false;
			}
		} else {
			logger.debug("Using default port");
			port = GatewayServer.DEFAULT_PORT;
		}
		
		if (port != null)
			builder.javaPort(port);
		
		var authToken = tfAuthToken.getText();
		if (authToken != null && !authToken.isEmpty()) {
			if (authToken.isBlank())
				logger.warn("Authentication token is blank (but not empty)");
			else
				logger.debug("Setting token");
			builder.authToken(authToken);
		}
		token = authToken;

		var server = builder.build();
		command.gatewayServer.set(server);
		
		return true;
	}
	
	
	private Node createGatewayIcon(ObservableBooleanValue gatewayOn) {
		var icon = new StackedFontIcon();
		icon.setIconCodeLiterals("ion4-logo-python");
		icon.setStyle("-fx-icon-color: -fx-text-fill;");
		icon.getStyleClass().add("qupath-icon");
		if (gatewayOn != null) {
			icon.styleProperty().bind(Bindings.createObjectBinding(() -> {
				if (gatewayOn.get()) {
					return "-fx-icon-color: rgb(200, 20, 20);";
				} else {
					return "-fx-icon-color: -fx-text-fill;";
				}
			}, gatewayOn));
		} else
			icon.setStyle("-fx-icon-color: -fx-text-fill;");
		return icon;
	}
	
	

	@Override
	public String getName() {
		return "QuPath Py4J extension";
	}

	@Override
	public String getDescription() {
		return "Connect QuPath to Python via Py4J";
	}
	
	// TODO: Update when version is stabilized
	@Override
	public Version getQuPathVersion() {
		return Version.parse("v0.4.0-SNAPSHOT");
	}
	
	
	
	
	private static class Py4JCommand {
		
		private static final Logger logger = LoggerFactory.getLogger(Py4JCommand.class);
		
		private BooleanProperty gatewayRunning = new SimpleBooleanProperty(false);
		
		private ObjectProperty<GatewayServer> gatewayServer = new SimpleObjectProperty<>();;
		
		private Py4JListener listener;
		
		private Py4JCommand() {
			gatewayServer.addListener(this::gatewayChanged);
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
			public void serverPreShutdown() {}
	
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
	

}
