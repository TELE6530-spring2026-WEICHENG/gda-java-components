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

import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;

/**
 * AWS IoT Core cloud client connector.
 *
 * Publishes the full SensorData JSON (not the Ubidots time/value shape)
 * so that IoT Rules SQL and downstream Lambda functions can access every
 * field (name, value, timeStamp, locationID, statusCode, etc.).
 *
 * Uses the AWS-recommended hierarchical topic convention:
 *     {baseTopic}/{deviceName}/{resourceType}/{itemName}
 * instead of the "-" suffix used by the Ubidots connector, so that
 * wildcard subscriptions such as "piot/gda/+/sensor/#" work as expected.
 */
public class AwsIotCoreClientConnector extends AbstractCloudClientConnector
{
	// static

	private static final Logger _Logger =
		Logger.getLogger(AwsIotCoreClientConnector.class.getName());

	// constructors

	public AwsIotCoreClientConnector()
	{
		super();
	}

	// protected methods - post-connect hook

	/**
	 * Post-connect hook invoked by AbstractCloudClientConnector.onConnect() once
	 * the AWS IoT Core MQTT + TLS handshake is fully established. This is the
	 * only safe place to issue subscriptions against AWS — doing it any earlier
	 * (e.g. right after connectClient() returns) would race the async handshake.
	 *
	 * Flow it enables:
	 *   Lambda --(AWS publish)--> piot/gda/ConstrainedDevice/ActuatorCmd/<item>
	 *        --> this listener    --> DeviceDataManager.handleActuatorCommandRequest()
	 *        --> local MQTT publish on PIOT/ConstrainedDevice/ActuatorCmd
	 *        --> CDA receives command
	 */
	@Override
	protected void handleCloudConnectComplete()
	{
		if (this.mqttClient == null || !this.mqttClient.isConnected()) {
			_Logger.warning(
				"Cloud MQTT client not connected. Cannot subscribe to actuator command topic.");
			return;
		}

		// Dispatch target for parsed commands. We capture the CDA enum here (not
		// the AWS topic string) because DeviceDataManager.sendActuatorCommandtoCda()
		// uses ResourceNameEnum.getResourceName() to pick the local-broker topic,
		// which performs the cloud-topic -> local-topic translation for us.
		ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;

		// Build the AWS-style topic filter:
		//   "piot/gda/" + deviceName + "/" + resourceType + "/#"
		//   = "piot/gda/ConstrainedDevice/ActuatorCmd/#"
		// The trailing "#" is an MQTT multi-level wildcard that matches any
		// item-level suffix (HumidifierActuator, LedActuator, etc.) the Lambda
		// might publish to.
		String cloudTopic = createBaseTopicName(resource) + "/#";

		_Logger.info("Subscribing to cloud actuator command topic: " + cloudTopic);

		// Per-topic IMqttMessageListener (lambda). Paho will invoke this ONLY for
		// messages whose topic matches the filter above, bypassing the shared
		// MqttClientConnector.messageArrived() routing. This keeps cloud-specific
		// topic parsing isolated to this class -- MqttClientConnector stays a
		// pure transport layer and knows nothing about AWS topic conventions.
		//
		// Threading: the body below runs on Paho's MQTT receive thread, NOT the
		// thread that called subscribeToTopic(). Keep it fast -- parse + dispatch
		// only, no blocking I/O.
		//
		// Closure: the lambda captures 'resource' (an effectively-final local)
		// and 'this' (for this.dataMsgListener). 'topic' is the ACTUAL published
		// topic string ("piot/gda/.../HumidifierActuator"), not the filter --
		// the "#" never appears in what Paho hands us.
		this.mqttClient.subscribeToTopic(cloudTopic, this.qosLevel, (topic, msg) -> {
			_Logger.info("Cloud actuator command arrived on topic: " + topic);

			try {
				// Decode raw MQTT payload bytes to a UTF-8 JSON string, then
				// deserialize to ActuatorData via DataUtil (Gson-backed). Field
				// names in the JSON must match the Java class exactly (command,
				// value, stateData, isResponse, plus BaseIotData fields).
				String payload = new String(msg.getPayload());
				ActuatorData data = DataUtil.getInstance().jsonToActuatorData(payload);

				if (data == null) {
					_Logger.warning("Failed to parse cloud actuator payload on topic: " + topic);
					return;
				}

				// Hand the parsed command to DeviceDataManager. It will then
				// publish to the local broker on the CDA_ACTUATOR_CMD_RESOURCE
				// topic, completing the cloud -> local bridge.
				if (this.dataMsgListener != null) {
					this.dataMsgListener.handleActuatorCommandRequest(resource, data);
				} else {
					_Logger.warning(
						"No dataMsgListener registered on cloud connector. Dropping command.");
				}
			} catch (Exception e) {
				// Swallow exceptions inside the listener. If we let them escape,
				// Paho may drop subsequent messages on this subscription.
				_Logger.warning("Cloud actuator handler failed: " + e.getMessage());
			}
		});
	}

	// public methods - subscribe flow

	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;

		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			String topicName = createBaseTopicName(resource);

			this.mqttClient.subscribeToTopic(topicName, this.qosLevel);

			success = true;
		} else {
			_Logger.warning(
				"No MQTT connection to AWS IoT Core. Cannot subscribe. Resource: " + resource);
		}

		return success;
	}

	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;

		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			String topicName = createBaseTopicName(resource);

			this.mqttClient.unsubscribeFromTopic(topicName);

			success = true;
		} else {
			_Logger.warning(
				"No MQTT connection to AWS IoT Core. Cannot unsubscribe. Resource: " + resource);
		}

		return success;
	}

	// protected methods - provider-specific hooks

	@Override
	protected String formatSensorDataPayload(SensorData data)
	{
		// AWS IoT Rules SQL can query any JSON field directly, so we send the
		// full SensorData JSON instead of the Ubidots time/value shape.
		return DataUtil.getInstance().sensorDataToJson(data);
	}

	@Override
	protected String createTopicName(ResourceNameEnum resource, String itemName)
	{
		return createBaseTopicName(resource) + "/" + itemName;
	}

	// private methods

	// AWS IoT Core hierarchical topic:
	// {baseTopic}/{deviceName}/{resourceType}
	// AWS topic names must NOT start with "/", so strip any leading slash
	// that the base topicPrefix may have introduced.
	private String createBaseTopicName(ResourceNameEnum resource)
	{
		String prefix = this.topicPrefix;

		if (prefix.startsWith("/")) {
			prefix = prefix.substring(1);
		}

		return prefix + resource.getDeviceName() + "/" + resource.getResourceType();
	}

}
