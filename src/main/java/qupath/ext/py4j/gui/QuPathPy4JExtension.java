/*-
 * Copyright 2024 QuPath developers, University of Edinburgh
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

package qupath.ext.py4j.gui;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.javafx.StackedFontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import py4j.GatewayServer;
import qupath.ext.py4j.core.GatewayManager;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;


/**
 * QuPath extension to enable Python and Java/QuPath to communicate, via <a href="http://py4j.org">Py4J</a>.
 *
 * @author Pete Bankhead
 */
public class QuPathPy4JExtension implements QuPathExtension, GitHubProject {

	private static final BooleanProperty enablePy4J = PathPrefs.createPersistentPreference("enablePy4J", true);
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.5.1");
	private static final Logger logger = LoggerFactory.getLogger(QuPathPy4JExtension.class);
	private static final ResourceBundle resources = UiUtils.getResources();
	private boolean isInstalled = false;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}

		qupath.getPreferencePane().getPropertySheet().getItems().add(new PropertyItemBuilder<>(enablePy4J, Boolean.class)
				.resourceManager(QuPathResources.getLocalizedResourceManager())
				.name(resources.getString("PreferencePane.name"))
				.category(resources.getString("PreferencePane.category"))
				.description(resources.getString("PreferencePane.description"))
				.build()
		);

		GatewayManager gatewayManager = new GatewayManager();
		gatewayManager.isRunning().addListener((p, o, n) -> Platform.runLater(() -> {
			if (n) {
				Dialogs.showInfoNotification("Py4J", resources.getString("Extension.gatewayStarted"));
			} else {
				Dialogs.showInfoNotification("Py4J", resources.getString("Extension.gatewayStopped"));
			}
		}));

		MenuTools.addMenuItems(
				qupath.getMenu("Extensions", false),
				createPy4JMenu(gatewayManager)
		);

		qupath.getToolBar().getItems().add(createGatewayButton(gatewayManager));

		isInstalled = true;
	}

	@Override
	public String getName() {
		return resources.getString("Extension.name");
	}

	@Override
	public String getDescription() {
		return resources.getString("Extension.description");
	}

	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}

	@Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(resources.getString("Extension.name"), "qupath", "qupath-extension-py4j");
	}

	private MenuItem createPy4JMenu(GatewayManager gatewayManager) {
		MenuItem gatewayMenu = new MenuItem();
		gatewayMenu.setGraphic(createGatewayIcon(gatewayManager.isRunning()));
		gatewayMenu.setOnAction(e -> startOrStopGateway(gatewayManager));

		if (gatewayManager.isRunning().get()) {
			gatewayMenu.setText(resources.getString("Extension.stopGateway"));
		} else {
			gatewayMenu.setText(resources.getString("Extension.startGateway"));
		}
		gatewayManager.isRunning().addListener((p, o, n) -> Platform.runLater(() -> {
			if (n) {
				gatewayMenu.setText(resources.getString("Extension.stopGateway"));
			} else {
				gatewayMenu.setText(resources.getString("Extension.startGateway"));
			}
		}));

		Menu py4JMenu = new Menu("Py4J");
		py4JMenu.visibleProperty().bind(enablePy4J);
		py4JMenu.getItems().add(gatewayMenu);

		return py4JMenu;
	}

	private Button createGatewayButton(GatewayManager gatewayManager) {
		Button gatewayButton = new Button();

		gatewayButton.setTooltip(new Tooltip(resources.getString("Extension.startGatewayDescription")));
		gatewayButton.visibleProperty().bind(enablePy4J);
		gatewayButton.setGraphic(createGatewayIcon(gatewayManager.isRunning()));
		gatewayButton.setOnAction(e -> startOrStopGateway(gatewayManager));

		return gatewayButton;
	}

	private Node createGatewayIcon(ObservableBooleanValue gatewayRunning) {
		StackedFontIcon icon = new StackedFontIcon();

		icon.setIconCodeLiterals("ion4-logo-python");
		icon.getStyleClass().add("qupath-icon");

		if (gatewayRunning.get()) {
			icon.setStyle("-fx-icon-color: rgb(200, 20, 20);");
		} else {
			icon.setStyle("-fx-icon-color: -fx-text-fill;");
		}
		gatewayRunning.addListener((p, o, n) -> Platform.runLater(() -> {
			if (n) {
				icon.setStyle("-fx-icon-color: rgb(200, 20, 20);");
			} else {
				icon.setStyle("-fx-icon-color: -fx-text-fill;");
			}
		}));

		return icon;
	}

	private void startOrStopGateway(GatewayManager gatewayManager) {
		if (gatewayManager.isRunning().get()) {
			gatewayManager.stop();
		} else {
			promptToStartGateway(gatewayManager);
		}
	}

	private void promptToStartGateway(GatewayManager gatewayManager) {
		GatewayCreator gatewayCreator;
		try {
			gatewayCreator = new GatewayCreator(GatewayServer.DEFAULT_PORT);
		} catch (IOException e) {
			logger.error("Error when creating gateway creator window", e);
			return;
		}

		Optional<ButtonType> buttonPressed = Dialogs.builder()
				.title(resources.getString("Extension.gateway"))
				.content(gatewayCreator)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.showAndWait();

		if (buttonPressed.isPresent() && buttonPressed.get().equals(ButtonType.OK)) {
			if (!gatewayCreator.getToken().isEmpty() && gatewayCreator.getToken().isBlank()) {
				logger.warn("Authentication token is blank but not empty");
			}

			gatewayManager.start(
					gatewayCreator.getPort(),
					gatewayCreator.getToken()
			);
		}
	}
}
