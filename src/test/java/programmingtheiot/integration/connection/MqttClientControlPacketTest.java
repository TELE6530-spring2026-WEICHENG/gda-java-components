/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */ 

package programmingtheiot.integration.connection;

import java.util.logging.Logger;

import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.MqttClientConnector;

/**
 * This test case class contains very basic integration tests for
 * MqttClientControlPacketTest. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 *
 */
public class MqttClientControlPacketTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientControlPacketTest.class.getName());
	
	
	// member var's
	
	private MqttClientConnector mqttClient = null;
	
	
	// test setup methods
	
	@Before
	public void setUp() throws Exception
	{
		this.mqttClient = new MqttClientConnector();
		
	}
	
	@After
	public void tearDown() throws Exception
	{
		if (this.mqttClient != null) {
			this.mqttClient.disconnectClient();
		}
	}
	
	// test methods
	
	@Test
	public void testConnectAndDisconnect()
	{

		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
		
		assertTrue(mqttClient.connectClient());
		// Test reconnection of duplicate client ID
		assertFalse(mqttClient.connectClient());
		
		try {
			Thread.sleep(delay * 1000);

		} catch (Exception e) {

			_Logger.warning("Exception while sleeping: " + e.getMessage());
			
		}

		assertTrue(mqttClient.disconnectClient());
		assertFalse(mqttClient.disconnectClient());

	}
	
	@Test
	public void testServerPing()
	{

		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
		
		assertTrue(mqttClient.connectClient());
		assertFalse(mqttClient.connectClient());

		try {
			// Sleep for a duration longer than the keep-alive interval to ensure that the client sends a PINGREQ and receives a PINGRESP
			// 1.5x the keep-alive interval to trigger keep-alive mechanism 
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
		_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		// Make sure the client is still connected after the keep-alive interval
		assertTrue(mqttClient.isConnected());
		
	}

	@Test
	public void testPublishAndSubscribe()
	{	
		int qos = 0;
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);

		// Connect the client before subscribing
		assertTrue(mqttClient.connectClient());

		// Subscribe to topics before publishing messages
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, qos));
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_REGISTRATION_REQUEST_RESOURCE, qos));

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, "test message for topic: GDA_MGMT_STATUS_CMD_RESOURCE", qos));
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, "test message for topic: GDA_MGMT_STATUS_MSG_RESOURCE", qos));
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_REGISTRATION_REQUEST_RESOURCE, "test message for topic: GDA_REGISTRATION_REQUEST_RESOURCE", qos));
	}

	
	/**
	 * Test to see control packages workflow 
	 */
	@Test
	public void testPubSubWithQos1()
	{
		int qos = 1;
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);

		assertTrue(mqttClient.connectClient());

		// Subscribe at QoS 1 so the broker acknowledges each incoming message with PUBACK
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, qos));
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		// QoS 1 publish: broker responds with PUBACK after each PUBLISH
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, "QoS 1 test - GDA_MGMT_STATUS_CMD_RESOURCE", qos));
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, "QoS 1 test - GDA_MGMT_STATUS_MSG_RESOURCE", qos));

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		assertTrue(mqttClient.disconnectClient());
	}


	/**
	 * Test to see control packages workflow PUBLISH -> PUBREC -> PUBREL -> PUBCOMP (4-way handshake)
	 */
	@Test
	public void testPubSubWithQos2()
	{
		int qos = 2;
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);

		assertTrue(mqttClient.connectClient());

		// Subscribe at QoS 2 so each incoming message goes through the full 4-way handshake
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, qos));
		assertTrue(mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		// QoS 2 publish: PUBLISH -> PUBREC -> PUBREL -> PUBCOMP (4-way handshake)
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, "QoS 2 test - GDA_MGMT_STATUS_CMD_RESOURCE", qos));
		assertTrue(mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, "QoS 2 test - GDA_MGMT_STATUS_MSG_RESOURCE", qos));

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			_Logger.warning("Exception while sleeping: " + e.getMessage());
		}

		assertTrue(mqttClient.disconnectClient());
	}


	@Test
	public void testForcedDisconnect()
	{
	}
	
}
