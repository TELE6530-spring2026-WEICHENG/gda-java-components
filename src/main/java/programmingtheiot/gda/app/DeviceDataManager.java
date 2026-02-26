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

import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's
	
	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = true;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private SystemPerformanceManager sysPerfManager = null;
	
	// constructors
	
	public DeviceDataManager()
	{
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();
	
		this.enableMqttClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		
		this.enableCoapServer =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		
		this.enableCloudClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);
		
		this.enablePersistenceClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);

		this.enableSystemPerf =
			configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE,  ConfigConst.ENABLE_SYSTEM_PERF_KEY);
		
		initConnections();
	}
	
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		initConnections();
	}
	
	
	// public methods
	
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.info("handleActuatorCommandResponse called with resource: " + resourceName.getResourceName());

			if (data.getStatusCode() != ConfigConst.DEFAULT_STATUS) {
				_Logger.warning("ActuatorData response indicates an error. Status code: " + data.getStatusCode());
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		return false;
	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		_Logger.info("handleIncomingMessage called with resource: " + resourceName.getResourceName());

		if (msg != null) {
			return true;
		}

		return false;
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		if (data != null) {
			_Logger.info("handleSensorMessage called with resource: " + resourceName.getResourceName());

			if (data.hasError()) {
			_Logger.warning("Error flag set for SensorData instance.");
		}
			String jsonData = DataUtil.getInstance().sensorDataToJson(data);

			boolean success = handleUpstreamTransmission(resourceName, jsonData, 1);

			this.handleIncomingDataAnalysis(resourceName, data);

			return true;
		}

		return false;
	}

	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		if (data != null) {
			_Logger.info("handleSystemPerformanceMessage called with resource: " + resourceName.getResourceName());
			
			if (data.hasError()) {
			_Logger.warning("Error flag set for SystemPerformanceData instance.");
		}
			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(data);
			boolean success = handleUpstreamTransmission(resourceName, jsonData, 1);

			

			return true;
		}

		return false;
	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		
	}
	
	public void startManager()
	{
		_Logger.info("DeviceDataManager is starting...");

		if (this.sysPerfManager != null) {
			this.sysPerfManager.startManager();
		}

		if (this.enableMqttClient && this.mqttClient != null) {
			if(this.mqttClient.connectClient()){


				_Logger.info("MQTT client connected successfully.");


				int qos = ConfigConst.DEFAULT_QOS;

				this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);

			} else {
				_Logger.warning("Failed to connect MQTT client.");
				// May add retry logic or hard fail here depending on requirements
			}
		}

		if (this.enableCoapServer && this.coapServer != null) {
			boolean success = this.coapServer.startServer();
			_Logger.info("CoAP server start: " + (success ? "succeeded" : "failed"));
		}

		if (this.enableCloudClient && this.cloudClient != null) {
			boolean success = this.cloudClient.connectClient();
			_Logger.info("Cloud client connection: " + (success ? "succeeded" : "failed"));
		}

		_Logger.info("DeviceDataManager started successfully.");
	}
	
	public void stopManager()
	{
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
			_Logger.info("CoAP server stop: " + (success ? "succeeded" : "failed"));
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
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 * 
	 */
	private void initConnections()
	{
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
			// TODO: implement this in Lab Module 8
		}
		
		if (this.enableCloudClient) {
			// TODO: implement this in Lab Module 10
		}
		
		if (this.enablePersistenceClient) {
			// TODO: implement this as an optional exercise in Lab Module 5
		}
	}

	/*
		Forward JSON data to the cloud client and persistence client
	 */
	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, String jsonData, int qos)
	{
		_Logger.fine("handleUpstreamTransmission called. Resource: " + resourceName.getResourceName());

		return false;
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.fine("handleIncomingDataAnalysis called. Resource: " + resourceName.getResourceName());

	}
	
}
