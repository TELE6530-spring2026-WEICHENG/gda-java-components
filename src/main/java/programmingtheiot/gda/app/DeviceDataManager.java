/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * You may find it more helpful to your design to adjust the
 * functionality, constants and interfaces (if there are any)
 * provided within in order to meet the needs of your specific
 * Programming the Internet of Things project.
 */

package programmingtheiot.gda.app;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.BaseIotData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.CloudClientFactory;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.ICloudClient;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DeviceDataManager implements IDataMessageListener {
	// static

	private static final Logger _Logger = Logger.getLogger(DeviceDataManager.class.getName());

	// private var's

	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = true;

	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private SystemPerformanceManager sysPerfManager = null;

	private boolean handleHumidityChangeOnDevice = false;
	private float triggerFanFloor = 50.0f;
	private float triggerFanCeiling = 70.0f;
	private long humidityMaxTimePastThreshold = 300;
	private int lastKnownFanCommand = ConfigConst.OFF_COMMAND;
	private int lastKnownWaterPumpCommand = ConfigConst.OFF_COMMAND;

	private SensorData latestHumiditySensorData = null;
	private OffsetDateTime latestHumiditySensorTimeStamp = null;
	private ActuatorData latestFanActuatorData = null;
	private Map<String, ActuatorData> actuatorResponseCache = new HashMap<>();

	private int defaultQos = ConfigConst.DEFAULT_QOS;

	// constructors

	public DeviceDataManager() {
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableMqttClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);

		this.enableCoapServer = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);

		this.enableCloudClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);

		this.enablePersistenceClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);

		this.enableSystemPerf = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_SYSTEM_PERF_KEY);

		this.handleHumidityChangeOnDevice = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.HANDLE_HUMIDITY_CHANGE_ON_DEVICE_KEY);

		this.triggerFanFloor = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_FAN_FLOOR_KEY, this.triggerFanFloor);

		this.triggerFanCeiling = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_FAN_CEILING_KEY, this.triggerFanCeiling);

		this.humidityMaxTimePastThreshold = configUtil.getInteger(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.HUMIDITY_MAX_TIME_PAST_THRESHOLD_KEY,
				(int) this.humidityMaxTimePastThreshold);

		this.defaultQos = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.DEFAULT_QOS_KEY, ConfigConst.DEFAULT_QOS);

		initConnections();

	}

	public DeviceDataManager(
			boolean enableMqttClient,
			boolean enableCoapClient,
			boolean enableCloudClient,
			boolean enableSmtpClient,
			boolean enablePersistenceClient) {
		super();

		initConnections();
	}

	// public methods

	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("handleActuatorCommandResponse called with resource: " + resourceName.getResourceName());

			if (data.getStatusCode() != ConfigConst.DEFAULT_STATUS) {
				_Logger.warning("ActuatorData response indicates an error. Status code: " + data.getStatusCode());
			}

			this.actuatorResponseCache.put(data.getName(), data);

			reconcileActuatorResponse(data);

			return true;
		}

		return false;
	}

	/*
	 * Compare an incoming ActuatorData response against the last-known
	 * requested command for the same actuator. A mismatch means the CDA
	 * either rejected, transformed, or lost the request — worth a warning
	 * so later logic (retry / mark-unhealthy / alert) can act on it.
	 */
	private void reconcileActuatorResponse(ActuatorData response) {
		String name = response.getName();
		int expected;

		if (ConfigConst.FAN_ACTUATOR_NAME.equals(name)) {
			expected = this.lastKnownFanCommand;
		} else if (ConfigConst.WATER_PUMP_ACTUATOR_NAME.equals(name)) {
			expected = this.lastKnownWaterPumpCommand;
		} else {
			return;
		}

		if (response.getCommand() != expected) {
			_Logger.warning(
					name + " command mismatch. Expected: " + expected
							+ ", received: " + response.getCommand());
		}
	}

	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("handleActuatorCommandRequest called with resource: " + resourceName.getResourceName());

			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}

			this.sendActuatorCommandtoCda(resourceName, data);

			return true;
		} else {
			_Logger.warning("Received null ActuatorData for resource: " + resourceName.getResourceName());
			return false;
		}

	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg) {
		_Logger.info("handleIncomingMessage called with resource: " + resourceName.getResourceName());

		if (msg != null) {
			return true;
		}

		return false;
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data) {
		if (data != null) {
			_Logger.info("handleSensorMessage called with resource: " + resourceName.getResourceName());

			if (data.hasError()) {
				_Logger.warning("Error flag set for SensorData instance.");
			}

			boolean success = handleUpstreamTransmission(resourceName, data, defaultQos);

			_Logger.info("Upstream transmission of SensorData was " + (success ? "successful" : "unsuccessful"));

			handleIncomingDataAnalysis(resourceName, data);

			return true;
		}

		return false;
	}

	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data) {
		if (data != null) {
			_Logger.info("handleSystemPerformanceMessage called with resource: " + resourceName.getResourceName());

			if (data.hasError()) {
				_Logger.warning("Error flag set for SystemPerformanceData instance.");
			}

			boolean success = handleUpstreamTransmission(resourceName, data, defaultQos);

			_Logger.info(
					"Upstream transmission of SystemPerformanceData was " + (success ? "successful" : "unsuccessful"));

			return true;
		}

		return false;
	}

	public void setActuatorDataListener(String name, IActuatorDataListener listener) {
		if (listener != null) {
			this.actuatorDataListener = listener;
			_Logger.info("ActuatorDataListener set for: " + name);
		} else {
			_Logger.warning("Attempted to set null ActuatorDataListener for: " + name);
		}

	}

	public void startManager() {
		_Logger.info("DeviceDataManager is starting...");

		if (this.sysPerfManager != null) {
			this.sysPerfManager.startManager();
		}

		if (this.enableMqttClient && this.mqttClient != null) {
			if (this.mqttClient.connectClient()) {

				_Logger.info("MQTT client connected successfully.");

			} else {
				_Logger.warning("Failed to connect MQTT client.");
				// May add retry logic or hard fail here depending on requirements
			}
		}

		if (this.enableCoapServer && this.coapServer != null) {
			boolean success = this.coapServer.startServer();

			if (success) {
				_Logger.info("CoAP server started successfully.");
			} else {
				_Logger.warning("Failed to start CoAP server.");
			}

		}

		if (this.enableCloudClient && this.cloudClient != null) {
			boolean success = this.cloudClient.connectClient();
			_Logger.info("Cloud client connection: " + (success ? "succeeded" : "failed"));
		}

		_Logger.info("DeviceDataManager started successfully.");
	}

	public void stopManager() {
		_Logger.info("DeviceDataManager is stopping...");

		if (this.sysPerfManager != null) {
			this.sysPerfManager.stopManager();
		}

		if (this.enableMqttClient && this.mqttClient != null) {

			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);

			if (this.mqttClient.disconnectClient()) {
				_Logger.info("Successfully disconnected MQTT client from broker.");
			} else {
				_Logger.severe("Failed to disconnect MQTT client from broker.");
			}

			if (this.enableCoapServer && this.coapServer != null) {
				boolean success = this.coapServer.stopServer();
				if (success) {
					_Logger.info("CoAP server stopped successfully.");
				} else {
					_Logger.warning("Failed to stop CoAP server.");
				}

			}

			if (this.enableCloudClient && this.cloudClient != null) {
				boolean success = this.cloudClient.disconnectClient();
				_Logger.info("Cloud client disconnect: " + (success ? "succeeded" : "failed"));
			}

			_Logger.info("DeviceDataManager stopped successfully.");
		}
	}

	// private methods

	/**
	 * Initializes the enabled connections. This will NOT start them, but only
	 * create the
	 * instances that will be used in the {@link #startManager() and #stopManager())
	 * methods.
	 * 
	 */
	private void initConnections() {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		if (this.enableSystemPerf) {
			this.sysPerfManager = new SystemPerformanceManager();
			this.sysPerfManager.setDataMessageListener(this);
		}

		if (this.enableMqttClient) {
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
		}

		if (this.enableCoapServer) {
			this.coapServer = new CoapServerGateway(this);

		}

		if (this.enableCloudClient) {
			this.cloudClient = CloudClientFactory.getInstance().getCloudClient();
			this.cloudClient.setDataMessageListener(this);
		}

		if (this.enablePersistenceClient) {
			// TODO: implement this as an optional exercise in Lab Module 5
		}
	}

	/*
	 * Forward typed IoT data to the cloud client (and, in future, the
	 * persistence client). Dispatches on runtime type so each branch can
	 * call the correct strongly-typed overload on ICloudClient.
	 */
	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, BaseIotData data, int qos) {
		_Logger.info(
				"handleUpstreamTransmission called for resource: " + resourceName.getResourceName()
						+ ", data: " + (data != null ? data.getName() : "null"));

		if (data == null) {
			return false;
		}

		boolean success = false;

		if (this.enableCloudClient && this.cloudClient != null) {
			if (data instanceof SensorData) {
				success = this.cloudClient.sendEdgeDataToCloud(resourceName, (SensorData) data);
			} else if (data instanceof SystemPerformanceData) {
				success = this.cloudClient.sendEdgeDataToCloud(resourceName, (SystemPerformanceData) data);
			} else {
				_Logger.warning(
						"Unsupported BaseIotData subtype for upstream transmission: "
								+ data.getClass().getSimpleName());
			}

			if (!success) {
				_Logger.warning(
						"Cloud upstream transmission returned false for resource: "
								+ resourceName.getResourceName());
			}
		}

		return success;
	}

	// Handle BaseIotData types: SensorData and SystemPerformanceData
	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, BaseIotData data) {
		_Logger.info("handleIncomingDataAnalysis called. Resource: " + resourceName.getResourceName());

		if (data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE) {
			if (handleHumidityChangeOnDevice) {
				SensorData sensorData = (SensorData) data;
				handleHumiditySensorAnalysis(resourceName, sensorData);
			}
		} else if (data.getTypeID() == ConfigConst.SYSTEM_PERF_TYPE) {
			SystemPerformanceData sysPerfData = (SystemPerformanceData) data;
			handleSystemPerformanceAnalysis(resourceName, sysPerfData);
		}

	}

	private void handleHumiditySensorAnalysis(ResourceNameEnum resource, SensorData sensorData) {

		_Logger.info("Analyzing humidity data from CDA: " + sensorData.getLocationID() + ". Value: "
				+ sensorData.getValue());

		boolean isHigh = sensorData.getValue() > this.triggerFanCeiling;
		boolean isBackToNormal = sensorData.getValue() < this.triggerFanFloor;

		if (isHigh && this.lastKnownFanCommand != ConfigConst.ON_COMMAND) {
			if (this.latestHumiditySensorData == null) {
				this.latestHumiditySensorData = sensorData;
				this.latestHumiditySensorTimeStamp = getDateTimeFromData(sensorData);

				_Logger.info(
						"Humidity above fan ceiling. Starting debounce timer: "
								+ this.humidityMaxTimePastThreshold + " seconds");

				return;
			}

			OffsetDateTime curTimeStamp = getDateTimeFromData(sensorData);
			long diffSeconds = ChronoUnit.SECONDS.between(
					this.latestHumiditySensorTimeStamp, curTimeStamp);

			_Logger.info("Checking fan trigger time delta: " + diffSeconds);

			if (diffSeconds >= this.humidityMaxTimePastThreshold) {
				ActuatorData ad = new ActuatorData();
				ad.setName(ConfigConst.FAN_ACTUATOR_NAME);
				ad.setLocationID(sensorData.getLocationID());
				ad.setTypeID(ConfigConst.FAN_ACTUATOR_TYPE);
				ad.setValue(sensorData.getValue());
				ad.setCommand(ConfigConst.ON_COMMAND);

				_Logger.info(
						"Humidity sustained above fan ceiling. Sending FAN ON to CDA: " + ad);

				this.lastKnownFanCommand = ad.getCommand();
				sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, ad);

				this.latestFanActuatorData = ad;
				this.latestHumiditySensorData = null;
				this.latestHumiditySensorTimeStamp = null;
			}
		} else if (isBackToNormal && this.lastKnownFanCommand == ConfigConst.ON_COMMAND) {
			if (this.latestFanActuatorData != null) {
				this.latestFanActuatorData.setCommand(ConfigConst.OFF_COMMAND);
				this.latestFanActuatorData.setValue(sensorData.getValue());

				_Logger.info(
						"Humidity dropped below fan floor (" + sensorData.getValue()
								+ " < " + this.triggerFanFloor
								+ "). Sending FAN OFF to CDA.");

				sendActuatorCommandtoCda(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, this.latestFanActuatorData);

				this.lastKnownFanCommand = this.latestFanActuatorData.getCommand();
				this.latestFanActuatorData = null;
				this.latestHumiditySensorData = null;
				this.latestHumiditySensorTimeStamp = null;
			} else {
				_Logger.warning(
						"ERROR: latestFanActuatorData is null when trying to send FAN OFF.");
			}
		}
	}

	private void handleSystemPerformanceAnalysis(ResourceNameEnum resource, SystemPerformanceData sysPerfData) {
		// TODO: Add any additional analysis or processing of SystemPerformanceData here
		// if needed
	}

	private void sendActuatorCommandtoCda(ResourceNameEnum resource, ActuatorData data) {

		// Send ActuatorData command to CDA via CoAP by using the observer pattern.
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
		}

		if (this.enableMqttClient && this.mqttClient != null) {
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			if (this.mqttClient.publishMessage(resource, jsonData, this.defaultQos)) {
				_Logger.info(
						"Published ActuatorData command from GDA to CDA: " + data.getCommand());
			} else {
				_Logger.warning(
						"Failed to publish ActuatorData command from GDA to CDA: " + data.getCommand());
			}
		}
	}

	private OffsetDateTime getDateTimeFromData(BaseIotData data) {
		OffsetDateTime odt = null;

		try {
			odt = OffsetDateTime.parse(data.getTimeStamp());
		} catch (Exception e) {
			_Logger.warning(
					"Failed to extract ISO 8601 timestamp from IoT data. Using local current time.");

			odt = OffsetDateTime.now();
		}

		return odt;
	}

}
