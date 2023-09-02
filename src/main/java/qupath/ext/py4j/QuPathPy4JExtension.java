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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.action.Action;
import org.kordamp.ikonli.javafx.StackedFontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.localization.LocalizedResourceManager;
import qupath.fx.prefs.annotations.BooleanPref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;


/**
 * QuPath extension to enable Python and Java/QuPath to communicate, via Py4J (http://py4j.org).
 * 
 * @author Pete Bankhead
 */
@PrefCategory("py4j.title")
public class QuPathPy4JExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPathPy4JExtension.class);

	@BooleanPref("py4j.enable")
	public BooleanProperty enablePy4J = PathPrefs.createPersistentPreference("enablePy4J", true);

	private GatewayManager gatewayManager = new GatewayManager();

	private LocalizedResourceManager resourceManager = LocalizedResourceManager.createInstance("qupath.ext.py4j.strings");

	private IntegerProperty portProperty = PathPrefs.createPersistentPreference("py4j.port", GatewayServer.DEFAULT_PORT);

	private String token = "";

	@Override
	public void installExtension(QuPathGUI qupath) {

		addAnnotatedPreferences(qupath);

		var action = createGatewayAction();

		var mi = ActionTools.createMenuItem(action);
		mi.graphicProperty().unbind();
		mi.setGraphic(createGatewayIcon(gatewayManager.gatewayRunningProperty()));
		mi.visibleProperty().bind(enablePy4J);
		
		var btn = ActionTools.createButtonWithGraphicOnly(action);
		btn.textProperty().unbind();
		btn.graphicProperty().unbind();
		btn.getStyleClass().add("toggle-button");

		gatewayManager.gatewayRunningProperty().addListener((v, o, n) -> {
			btn.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), n);
			if (n)
				Dialogs.showInfoNotification(getTitle(), resourceManager.getString("py4j.notifications.gateway_started"));
			else
				Dialogs.showInfoNotification(getTitle(), resourceManager.getString("py4j.notifications.gateway_stopped"));
		});
		btn.visibleProperty().bind(enablePy4J);
		
		btn.setGraphic(createGatewayIcon(gatewayManager.gatewayRunningProperty()));
		btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		
		qupath.getToolBar().getItems().add(btn);

		var menu = qupath.getMenu("Extensions>Py4J", true);
		menu.visibleProperty().bind(enablePy4J);
		menu.getItems().add(mi);
	}

	@Override
	public String getName() {
		return resourceManager.getString("py4j.extension.name");
	}

	@Override
	public String getDescription() {
		return resourceManager.getString("py4j.extension.description");
	}

	// TODO: Update when version is stabilized
	@Override
	public Version getQuPathVersion() {
		return Version.parse("v0.5.0-SNAPSHOT");
	}

	private void addAnnotatedPreferences(QuPathGUI qupath) {
		// Add any annotated preferences from this class
		qupath.getPreferencePane().getPropertySheet().getItems().addAll(
				PropertySheetUtils.parseAnnotatedItemsWithResources(LocalizedResourceManager.createInstance("qupath.ext.py4j.strings"), this)
		);
	}


	private String getTitle() {
		return resourceManager.getString("py4j.title");
	}

	
	private Action createGatewayAction() {
		var action = new Action(getTitle(), this::handleGatewayEvent);
		action.textProperty().bind(Bindings.createStringBinding(() -> {
			if (action.isSelected())
				return resourceManager.getString("py4j.gateway.stop");
			else
				return resourceManager.getString("py4j.gateway.start");
		}, action.selectedProperty()));

		action.longTextProperty().bind(resourceManager.createProperty("py4j.gateway.action.description"));
		return action;
	}


	private void handleGatewayEvent(ActionEvent e) {
		if (gatewayManager.isGatewayRunning())
			gatewayManager.stopGateway();
		else {
			try {
				promptToStartGateway();
			} catch (IOException e1) {
				Dialogs.showErrorMessage(getTitle(), e1);
			}
		}
	}

	
	private boolean promptToStartGateway() throws IOException {
		
		if (gatewayManager.isGatewayRunning()) {
			logger.warn("Can't start new GatewayServer - please shut down the existing server first");
			return false;
		}
		
		URL url = getClass().getResource("gateway_prompt.fxml");
		if (url == null) {
			throw new IOException("Can't find URL for Gateway dialog");
		}

		var loader = new FXMLLoader(url);
		loader.setResources(ResourceBundle.getBundle(resourceManager.getDefaultBundleName()));
		GridPane pane = loader.load();
		GatewayPrompt controller = loader.getController();
		Integer port = portProperty.getValue();
		if (Objects.equals(GatewayServer.DEFAULT_PORT, port))
			controller.setPort(null);
		else
			controller.setPort(Integer.toString(port));
		controller.setToken(token);

		var result = Dialogs.builder()
				.title(getTitle())
				.content(pane)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;
		
		if (!result)
			return false;

		var builder = new GatewayServer.GatewayServerBuilder()
				.entryPoint(new QuPathEntryPoint());
		
		var portText = controller.getPort();
		if (portText != null && !portText.isBlank()) {
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException e) {
				Dialogs.showErrorMessage(getTitle(), resourceManager.getString("py4j.error.invalid_port"));
				return false;
			}
		} else {
			logger.debug("Using default port");
			port = GatewayServer.DEFAULT_PORT;
		}
		
		if (port != null) {
			builder.javaPort(port);
		}
		portProperty.set(port);

		var authToken = controller.getToken();
		if (authToken != null && !authToken.isEmpty()) {
			if (authToken.isBlank())
				logger.warn("Authentication token is blank (but not empty)");
			else
				logger.debug("Setting token");
			builder.authToken(authToken);
		}
		token = authToken;

		var server = builder.build();
		gatewayManager.gatewayServerProperty().set(server);
		
		return true;
	}
	
	
	private static Node createGatewayIcon(ObservableBooleanValue gatewayOn) {
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


}
