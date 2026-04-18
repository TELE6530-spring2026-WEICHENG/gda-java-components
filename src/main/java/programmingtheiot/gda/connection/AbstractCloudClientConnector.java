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

package programmingtheiot.gda.connection;

import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Template base class for cloud client connectors.
 *
 * Defines the shared connect / disconnect / publish flow and delegates
 * provider-specific decisions (payload format and topic naming) to
 * concrete subclasses via the abstract hook methods.
 */
public abstract class AbstractCloudClientConnector implements ICloudClient, IConnectionListener {
	// static

	private static final Logger _Logger = Logger.getLogger(AbstractCloudClientConnector.class.getName());

	// protected (visible to subclasses)

	protected String cloudServiceConfigSection = ConfigConst.CLOUD_GATEWAY_SERVICE;
	protected String topicPrefix = "";
	protected MqttClientConnector mqttClient = null;
	protected IDataMessageListener dataMsgListener = null;
	protected int qosLevel = ConfigConst.DEFAULT_QOS;

	// constructors

	protected AbstractCloudClientConnector() {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		// Resolve the provider-specific section (e.g. "Cloud.GatewayService.AWS")
		// by reading `cloudServiceName` from the base "Cloud.GatewayService"
		// section. This lets us keep provider-specific host / cert / topic
		// settings isolated in their own sub-section while the factory only
		// needs to flip a single key to switch providers.
		String cloudSvcName = configUtil.getProperty(
				ConfigConst.CLOUD_GATEWAY_SERVICE, ConfigConst.CLOUD_SERVICE_NAME_KEY);

		this.cloudServiceConfigSection = configUtil.getCloudSectionName(cloudSvcName);

		_Logger.info("Cloud client using config section: " + this.cloudServiceConfigSection);

		this.topicPrefix = configUtil.getProperty(this.cloudServiceConfigSection, ConfigConst.BASE_TOPIC_KEY);
		this.qosLevel = configUtil.getInteger(
				this.cloudServiceConfigSection,
				ConfigConst.DEFAULT_QOS_KEY,
				this.qosLevel);

		if (this.topicPrefix == null) {
			this.topicPrefix = "/";
		} else if (!this.topicPrefix.endsWith("/")) {
			this.topicPrefix += "/";
		}
	}

	// public methods - shared lifecycle

	@Override
	public boolean connectClient() {
		if (this.mqttClient == null) {
			this.mqttClient = new MqttClientConnector(this.cloudServiceConfigSection);
		}

		// AwsIotCoreClientConnector ──has──▶ MqttClientConnector
		// MqttClientConnector.connListener ──points back to──▶
		// AwsIotCoreClientConnector
		this.mqttClient.setConnectionListener(this);

		return this.mqttClient.connectClient();
	}

	// IConnectionListener - invoked from MqttClientConnector's connectComplete()
	// callback (Paho thread). Keep these methods lightweight and delegate the
	// provider-specific work to the hook methods below.

	@Override
	public void onConnect() {
		_Logger.info("Cloud client MQTT connection complete. Running post-connect hook.");

		handleCloudConnectComplete();
	}

	@Override
	public void onDisconnect() {
		_Logger.info("Cloud client MQTT connection lost. Running post-disconnect hook.");

		handleCloudDisconnect();
	}

	/**
	 * Provider-specific post-connect hook. Default is a no-op; subclasses
	 * override this to subscribe to cloud-originated topics (e.g. actuator
	 * commands from AWS IoT Core) once the broker handshake is truly complete.
	 */
	protected void handleCloudConnectComplete() {
	}

	/**
	 * Provider-specific post-disconnect hook.
	 * Subscribe to cloud-originated topics (e.g. actuator commands from AWS IoT
	 * Core) once the broker handshake is truly complete.
	 */
	protected void handleCloudDisconnect() {
	}

	@Override
	public boolean disconnectClient() {
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			return this.mqttClient.disconnectClient();
		}

		return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			this.dataMsgListener = listener;
			return true;
		}

		return false;
	}

	// public methods - send flow (template methods)

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data) {
		if (resource != null && data != null) {
			String payload = formatSensorDataPayload(data);

			return publishMessageToCloud(resource, data.getName(), payload);
		}

		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data) {
		if (resource != null && data != null) {
			SensorData cpuData = new SensorData();
			cpuData.updateData(data);
			cpuData.setName(ConfigConst.CPU_UTIL_NAME);
			cpuData.setValue(data.getCpuUtilization());

			boolean cpuDataSuccess = sendEdgeDataToCloud(resource, cpuData);

			if (!cpuDataSuccess) {
				_Logger.warning("Failed to send CPU utilization data to cloud service.");
			}

			SensorData memData = new SensorData();
			memData.updateData(data);
			memData.setName(ConfigConst.MEM_UTIL_NAME);
			memData.setValue(data.getMemoryUtilization());

			boolean memDataSuccess = sendEdgeDataToCloud(resource, memData);

			if (!memDataSuccess) {
				_Logger.warning("Failed to send memory utilization data to cloud service.");
			}

			return (cpuDataSuccess && memDataSuccess);
		}

		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, ActuatorData data) {
		if (resource != null && data != null) {
			String payload = formatActuatorDataPayload(data);

			return publishMessageToCloud(resource, data.getName(), payload);
		}

		return false;
	}

	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource) {
		return false;
	}

	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource) {
		return false;
	}

	// protected abstract hooks - provider-specific behavior

	/**
	 * Serialize a SensorData instance into the payload format expected by
	 * the concrete cloud provider (e.g. Ubidots time/value JSON, AWS full JSON).
	 */
	protected abstract String formatSensorDataPayload(SensorData data);

	/**
	 * Serialize an ActuatorData instance into the payload format expected by
	 * the concrete cloud provider.
	 */
	protected abstract String formatActuatorDataPayload(ActuatorData data);

	/**
	 * Build the full topic name for a given resource + item (e.g. "cpuUtil").
	 * Each provider has its own naming convention (Ubidots uses "-" suffix,
	 * AWS IoT Core prefers "/" hierarchy).
	 */
	protected abstract String createTopicName(ResourceNameEnum resource, String itemName);

	// protected methods - shared publish path

	protected boolean publishMessageToCloud(
			ResourceNameEnum resource, String itemName, String payload) {
		String topicName = createTopicName(resource, itemName);

		return publishMessageToCloud(topicName, payload);
	}

	protected boolean publishMessageToCloud(String topicName, String payload) {
		try {
			_Logger.finest("Publishing payload value(s) to CSP: " + topicName);

			this.mqttClient.publishMessage(topicName, payload, this.qosLevel);

			return true;
		} catch (Exception e) {
			_Logger.warning("Failed to publish message to CSP: " + topicName);
		}

		return false;
	}

}
