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
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.SensorData;
import programmingtheiot.gda.app.DeviceDataManager;
import programmingtheiot.gda.connection.MqttClientConnector;

/**
 * This test case class contains very basic integration tests for
 * MqttClientConnector. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 * 
 * IMPORTANT NOTE: This test expects MqttClientConnector to be
 * configured using the synchronous MqttClient.
 * 
 */
public class MqttClientConnectorTest {
	// static

	private static final Logger _Logger = Logger.getLogger(MqttClientConnectorTest.class.getName());

	// member var's

	// TODO: make sure MqttClientConnector is configured to
	// use the synchronous MqttClient
	private MqttClientConnector mqttClient = null;

	// test setup methods

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		this.mqttClient = new MqttClientConnector();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	// test methods

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#connectClient()}.
	 */
	@Test
	public void testConnectAndDisconnect() {
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE);

		assertTrue(this.mqttClient.connectClient());
		assertFalse(this.mqttClient.connectClient());

		try {
			Thread.sleep(delay * 1000 + 5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());
		assertFalse(this.mqttClient.disconnectClient());
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 */
	@Test
	public void testPublishAndSubscribe() {
		int qos = 0;
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE);

		assertTrue(this.mqttClient.connectClient());
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos));
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos));
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 1.", qos));
		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 2.", qos));
		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 3.", qos));

		try {
			Thread.sleep(25000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE));
		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE));
		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE));
		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		try {
			Thread.sleep(delay * 1000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 */
	// @Test
	public void testPublishAndSubscribeTwoClients() {
		int qos = 0;

		IDataMessageListener listener = new MqttPublishDataMessageListener(
				ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, true);

		this.mqttClient.setDataMessageListener(listener);

		assertTrue(this.mqttClient.connectClient());

		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, 0));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 1.", qos));
		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 2.", qos));
		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
				"TEST: This is the GDA message payload 3.", qos));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE));
		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 */
	// @Test
	public void testIntegrateWithCdaPublishCdaCmdTopic() {
		int qos = 1;

		assertTrue(this.mqttClient.connectClient());
		assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.CDA_MGMT_STATUS_CMD_RESOURCE,
				"TEST: This is the CDA command payload.", qos));

		try {
			Thread.sleep(60000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 */
	// @Test
	public void testIntegrateWithCdaSubscribeCdaMgmtTopic() {
		int qos = 1;
		int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE);

		assertTrue(this.mqttClient.connectClient());
		assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE, qos));

		try {
			Thread.sleep(delay * 1000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE));

		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());
	}

	/**
	 * Test method for running the DeviceDataManager.
	 * 
	 */
	@Test
	public void testSendActuationEventsToCda() {
		DeviceDataManager devDataMgr = new DeviceDataManager();

		// NOTE: Be sure your PiotConfig.props is setup properly
		// to connect with the CDA
		devDataMgr.startManager();

		ConfigUtil cfgUtil = ConfigUtil.getInstance();

		float nominalVal = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE, "nominalHumiditySetting");
		float lowVal = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE, "triggerHumidifierFloor");
		float highVal = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE, "triggerHumidifierCeiling");
		int delay = cfgUtil.getInteger(ConfigConst.GATEWAY_DEVICE, "humidityMaxTimePastThreshold");

		// Test Sequence No. 1: Low humidity -> triggers humidifier ON -> recovery to nominal -> OFF
		generateAndProcessHumiditySensorDataSequence(
				devDataMgr, nominalVal, lowVal, highVal, delay);

		// Test Sequence No. 2: High humidity -> triggers humidifier OFF command
		generateAndProcessHighHumiditySensorDataSequence(
				devDataMgr, nominalVal, highVal, delay);

		// Test Sequence No. 3: Full low-humidity cycle with recovery
		generateAndProcessLowHumidityWithRecoverySequence(
				devDataMgr, nominalVal, lowVal, delay);

		devDataMgr.stopManager();
	}

	private void generateAndProcessHumiditySensorDataSequence(
			DeviceDataManager ddm, float nominalVal, float lowVal, float highVal, int delay) {
		SensorData sd = new SensorData();
		sd.setName("My Test Humidity Sensor");
		sd.setLocationID("constraineddevice001");
		sd.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE);

		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		sd.setValue(lowVal - 2);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		sd.setValue(lowVal - 1);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		sd.setValue(lowVal + 1);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);
	}

	private void generateAndProcessHighHumiditySensorDataSequence(
			DeviceDataManager ddm, float nominalVal, float highVal, int delay) {
		SensorData sd = new SensorData();
		sd.setName("My Test Humidity Sensor - High");
		sd.setLocationID("constraineddevice001");
		sd.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE);

		// Start with nominal value (no actuation expected)
		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		// First reading above ceiling — starts the exception timer
		sd.setValue(highVal + 5);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		// Second reading above ceiling — exceeds threshold time, should trigger OFF command
		sd.setValue(highVal + 3);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		// Return to nominal — should reset state
		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);
	}

	private void generateAndProcessLowHumidityWithRecoverySequence(
			DeviceDataManager ddm, float nominalVal, float lowVal, int delay) {
		SensorData sd = new SensorData();
		sd.setName("My Test Humidity Sensor - Recovery");
		sd.setLocationID("constraineddevice001");
		sd.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE);

		// Start at nominal
		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		// Drop below floor — starts exception timer
		sd.setValue(lowVal - 5);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		// Still below floor — exceeds threshold, triggers ON command
		sd.setValue(lowVal - 3);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(delay + 1);

		// Humidity recovers but still below nominal — humidifier should stay ON
		sd.setValue(lowVal + 2);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		// Humidity reaches nominal — should trigger OFF command
		sd.setValue(nominalVal);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);

		// Stays at nominal — no further actuation expected
		sd.setValue(nominalVal + 1);
		ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
		waitForSeconds(2);
	}

	private void waitForSeconds(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException e) {
			// ignore
		}
	}

}
