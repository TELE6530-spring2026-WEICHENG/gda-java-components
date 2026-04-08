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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import java.io.File;
import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;
import programmingtheiot.common.SimpleCertManagementUtil;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended {
	// static

	private static final Logger _Logger = Logger.getLogger(MqttClientConnector.class.getName());

	// params
	private boolean useAsyncClient = false;

	private MqttAsyncClient mqttClient = null;
	private MqttConnectOptions connOpts = null;
	private MemoryPersistence persistence = null;
	private IDataMessageListener dataMsgListener = null;

	private String clientID = null;
	private String brokerAddr = null;
	private String host = ConfigConst.DEFAULT_HOST;
	private String protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
	private int port = ConfigConst.DEFAULT_MQTT_PORT;
	private int brokerKeepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
	private int defaultQos = ConfigConst.DEFAULT_QOS;

	// For secure connection parameters
	private String pemFileName = null;
	private boolean enableEncryption = false;
	private boolean useCleanSession = false;
	private boolean enableAutoReconnect = true;

	// constructors

	/**
	 * Default.
	 * 
	 */
	public MqttClientConnector() {
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.host = configUtil.getProperty(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);

		this.port = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_MQTT_PORT);

		this.brokerKeepAlive = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);

		this.useAsyncClient = configUtil.getBoolean(
				ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.USE_ASYNC_CLIENT_KEY);

		// MQTT connection parameters
		this.persistence = new MemoryPersistence();
		this.connOpts = new MqttConnectOptions();

		this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);

		// If using a random clientID for each new connection, clean session should be
		// 'true'
		// But we use a fixed clientID, so we can set clean session to 'false' to
		// maintain state across connections
		this.connOpts.setCleanSession(false);

		// For connection recovery feature
		this.connOpts.setAutomaticReconnect(true);

		// Set ClientID, credentials, and TLS (needs connOpts to exist first)
		initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);

		// NOTE: Java URL library does not have a protocol handler for "tcp",
		// so we need to construct the URL manually
		// Must be after initClientParameters() which may change protocol/port for TLS
		this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

	}

	// public methods

	@Override
	public boolean connectClient() {
		try {
			if (this.mqttClient == null) {
				this.mqttClient = new MqttAsyncClient(this.brokerAddr, this.clientID, this.persistence);
				this.mqttClient.setCallback(this);
			}

			if (!this.mqttClient.isConnected()) {
				_Logger.info("MQTT client connecting to broker: " + this.brokerAddr);

				IMqttToken token = this.mqttClient.connect(this.connOpts);
				token.waitForCompletion();

				return true;
			} else {
				_Logger.warning("MQTT client already connected to broker: " + this.brokerAddr);
			}
		} catch (MqttException e) {
			_Logger.warning("Failed to connect MQTT client to broker: " + this.brokerAddr
					+ " - reason: " + e.getReasonCode() + " - msg: " + e.getMessage());
		}

		return false;
	}

	@Override
	public boolean disconnectClient() {
		try {
			if (this.mqttClient != null) {
				if (this.mqttClient.isConnected()) {
					_Logger.info("Disconnecting MQTT client from broker: " + this.brokerAddr);

					IMqttToken token = this.mqttClient.disconnect();
					token.waitForCompletion();

					return true;
				} else {
					_Logger.warning("MQTT client not connected to broker: " + this.brokerAddr);
				}
			}
		} catch (Exception e) {
			_Logger.warning("Failed to disconnect MQTT client from broker: " + this.brokerAddr
					+ " - reason: " + e.getMessage());
		}

		return false;
	}

	public boolean isConnected() {
		return (this.mqttClient != null && this.mqttClient.isConnected());
	}

	@Override
	public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos) {
		if (topicName == null) {
			return false;
		}

		if (msg == null || msg.isEmpty()) {
			return false;
		}

		if (qos < 0 || qos > 2) {
			qos = this.defaultQos;
		}
		try {
			byte[] payload = msg.getBytes();
			MqttMessage mqttMsg = new MqttMessage(payload);
			mqttMsg.setQos(qos);

			IMqttToken token = this.mqttClient.publish(topicName.getResourceName(), mqttMsg);

			_Logger.info("Published message to topic: " + topicName.getResourceName());

			return true;
		} catch (Exception e) {
			_Logger.severe("Failed to publish MQTT message to topic: " + topicName.getResourceName() + " - reason: "
					+ e.getMessage());
		}
		return false;
	}

	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
		if (topicName == null) {
			_Logger.warning("Topic name is null, cannot subscribe to MQTT topic on broker: " + this.brokerAddr);
			return false;
		}
		if (qos < 0 || qos > 2) {
			qos = this.defaultQos;
		}
		try {
			IMqttToken token = this.mqttClient.subscribe(topicName.getResourceName(), qos);
			token.waitForCompletion();
			_Logger.info("Subscribing to topic: " + topicName.getResourceName() + " with QoS: " + qos);
			return true;
		} catch (Exception e) {
			_Logger.severe("Failed to subscribe to MQTT topic: " + topicName.getResourceName() + " - reason: "
					+ e.getMessage());
			return false;
		}
	}

	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);
			return false;
		}

		try {
			IMqttToken token = this.mqttClient.unsubscribe(topicName.getResourceName());
			token.waitForCompletion();
			_Logger.info("Successfully unsubscribed from topic: " + topicName.getResourceName());
			return true;
		} catch (Exception e) {
			_Logger.severe("Failed to unsubscribe from topic: " + topicName.getResourceName() + " - reason: "
					+ e.getMessage());
		}

		return false;
	}

	@Override
	public boolean setConnectionListener(IConnectionListener listener) {
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

	// callbacks

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		_Logger.info("MQTT connection successful (is reconnect = " + reconnect + "). Broker: " + serverURI);

		int qos = 1;

		this.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
		this.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
		this.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);

	}

	@Override
	public void connectionLost(Throwable t) {

		_Logger.warning("Lost connection to MQTT broker: " + this.brokerAddr + " - reason: " + t.getMessage());

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// _Logger.info("MqttClient callback - Delivered MQTT message with ID: " +
		// token.getMessageId());
	}

	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception {
		_Logger.info("MQTT message arrived on topic: '" + topic + "'");

		if (this.dataMsgListener != null) {
			ResourceNameEnum resource = ResourceNameEnum.getEnumFromValue(topic);

			if (resource != null) {
				String msgPayload = new String(msg.getPayload());

				try {
					if (resource == ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE) {
						SensorData sensorData = DataUtil.getInstance().jsonToSensorData(msgPayload);
						this.dataMsgListener.handleSensorMessage(resource, sensorData);

					} else if (resource == ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE) {
						SystemPerformanceData sysPerfData = DataUtil.getInstance()
								.jsonToSystemPerformanceData(msgPayload);
						this.dataMsgListener.handleSystemPerformanceMessage(resource, sysPerfData);

					} else if (resource == ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE) {
						ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(msgPayload);
						this.dataMsgListener.handleActuatorCommandResponse(resource, actuatorData);

					} else {
						_Logger.info("Unhandled topic, forwarding as raw message: " + topic);
						this.dataMsgListener.handleIncomingMessage(resource, msgPayload);
					}
				} catch (Exception e) {
					_Logger.warning("Failed to parse message on topic " + topic + ": " + e.getMessage());
				}
			} else {
				_Logger.warning("Unknown topic (no ResourceNameEnum match): " + topic);
			}
		} else {
			_Logger.warning("No IDataMessageListener registered. Ignoring message on: " + topic);
		}
	}

	// private methods

	/**
	 * Called by the constructor to set the MQTT client parameters to be used for
	 * the connection.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initClientParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.clientID = configUtil.getProperty(
				configSectionName, ConfigConst.CLIENT_ID_KEY, ConfigConst.MQTT_GATEWAY_CLIENT_ID);

		this.enableEncryption = configUtil.getBoolean(configSectionName, ConfigConst.ENABLE_CRYPT_KEY);

		this.pemFileName = configUtil.getProperty(configSectionName, ConfigConst.CERT_FILE_KEY);

		initCredentialConnectionParameters(configSectionName);

		if (this.enableEncryption) {
			initSecureConnectionParameters(configSectionName);
		}
	}

	/**
	 * Called by {@link #initClientParameters(String)} to load credentials.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initCredentialConnectionParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			_Logger.info("Checking if credentials file exists and is loadable...");

			Properties props = configUtil.getCredentials(configSectionName);

			if (props != null) {
				this.connOpts.setUserName(props.getProperty(ConfigConst.USER_NAME_TOKEN_KEY, ""));
				this.connOpts.setPassword(props.getProperty(ConfigConst.USER_AUTH_TOKEN_KEY, "").toCharArray());

				_Logger.info("Credentials now set.");
			} else {
				_Logger.warning("No credentials are set.");
			}
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Credential file non-existent. Disabling auth requirement.");
		}
	}

	/**
	 * Called by {@link #initClientParameters(String)} to enable encryption.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initSecureConnectionParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			_Logger.info("Configuring TLS...");

			if (this.pemFileName != null) {
				File file = new File(this.pemFileName);

				if (file.exists()) {
					_Logger.info("PEM file valid. Using secure connection: " + this.pemFileName);
				} else {
					this.enableEncryption = false;

					_Logger.log(Level.WARNING, "PEM file invalid. Using insecure connection: " + this.pemFileName,
							new Exception());

					return;
				}
			}

			SSLSocketFactory sslFactory = SimpleCertManagementUtil.getInstance().loadCertificate(this.pemFileName);

			this.connOpts.setSocketFactory(sslFactory);

			this.port = configUtil.getInteger(
					configSectionName, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_MQTT_SECURE_PORT);

			this.protocol = ConfigConst.DEFAULT_MQTT_SECURE_PROTOCOL;

			_Logger.info("TLS enabled.");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize secure MQTT connection. Using insecure connection.", e);

			this.enableEncryption = false;
		}
	}
}
