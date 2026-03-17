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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class RedisPersistenceAdapter implements IPersistenceClient {
	// static

	private static final Logger _Logger = Logger.getLogger(RedisPersistenceAdapter.class.getName());

	private static final RedisPersistenceAdapter _Instance = new RedisPersistenceAdapter();

	// public static

	public static final RedisPersistenceAdapter getInstance() {
		return _Instance;
	}

	// private var's

	private Jedis jedis = null;
	private String host;
	private int port;
	private long dedupTtlSecs;

	// constructors

	/**
	 * Default.
	 * 
	 */
	private RedisPersistenceAdapter() {
		super();

		initConfig();
		connectClient();
	}

	// public methods

	/**
	 *
	 */
	@Override
	public boolean connectClient() {
		if (this.jedis != null) {
			_Logger.info("Redis client already connected.");
			return true;
		}

		try {
			this.jedis = new Jedis(this.host, this.port);
			String response = this.jedis.ping();
			_Logger.info("Connected to Redis server. Ping response: " + response);
			return true;
		} catch (JedisConnectionException e) {
			_Logger.severe("Failed to connect to Redis: " + e.getMessage());
			this.jedis = null;
			return false;
		}
	}

	/**
	 *
	 */
	@Override
	public boolean disconnectClient() {
		if (this.jedis != null) {
			try {
				this.jedis.close();
				_Logger.info("Disconnected from Redis server.");
			} catch (Exception e) {
				_Logger.warning("Error closing Redis connection: " + e.getMessage());
			} finally {
				this.jedis = null;
			}
			return true;
		}

		return false;
	}

	/**
	 *
	 */
	@Override
	public ActuatorData[] getActuatorData(String topic, Date startDate, Date endDate) {
		return null;
	}

	/**
	 *
	 */
	@Override
	public SensorData[] getSensorData(String topic, Date startDate, Date endDate) {
		return null;
	}

	/**
	 *
	 */
	@Override
	public void registerDataStorageListener(Class cType, IPersistenceListener listener, String... topics) {
	}

	/**
	 *
	 */
	@Override
	public boolean storeData(String topic, int qos, ActuatorData... data) {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean storeData(String topic, int qos, SensorData... data) {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean storeData(String topic, int qos, SystemPerformanceData... data) {
		return false;
	}

	public boolean storeCoapData(String deviceName, long timeStampMillis, ActuatorData data) {
		String key = ConfigConst.COAP_IDEM_KEY_PREFIX + deviceName + ":" + timeStampMillis;
		return storeCoapDataInternal(key, DataUtil.getInstance().actuatorDataToJson(data));
	}

	public boolean storeCoapData(String deviceName, long timeStampMillis, SensorData data) {
		String key = ConfigConst.COAP_IDEM_KEY_PREFIX + deviceName + ":" + timeStampMillis;
		return storeCoapDataInternal(key, DataUtil.getInstance().sensorDataToJson(data));
	}

	public boolean storeCoapData(String deviceName, long timeStampMillis, SystemPerformanceData data) {
		String key = ConfigConst.COAP_IDEM_KEY_PREFIX + deviceName + ":" + timeStampMillis;
		return storeCoapDataInternal(key, DataUtil.getInstance().systemPerformanceDataToJson(data));
	}

	public boolean isDuplicate(String key) {
		if (this.jedis == null) {
			_Logger.warning("Redis client not connected. Cannot check for duplicate.");
			return false;
		}

		String result = this.jedis.set(key, "1", SetParams.setParams().nx().ex(this.dedupTtlSecs));

		// "OK" means the key was set (first time) → not duplicate
		// null means the key already existed → duplicate
		return (result == null);
	}

	// private methods

	private boolean storeCoapDataInternal(String key, String json) {
		if (this.jedis == null) {
			_Logger.warning("Redis client not connected. Cannot store CoAP data.");
			return false;
		}

		if (json == null) {
			_Logger.warning("Cannot store null JSON for key: " + key);
			return false;
		}

		String result = this.jedis.set(key, json, SetParams.setParams().nx().ex(this.dedupTtlSecs));

		if (result == null) {
			_Logger.info("Duplicate CoAP data detected for key: " + key);
			return false;
		}

		_Logger.info("Stored CoAP data with key: " + key);
		return true;
	}

	private void initConfig() {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.host = configUtil.getProperty(
				ConfigConst.REDIS_DATA_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
		this.port = configUtil.getInteger(
				ConfigConst.REDIS_DATA_GATEWAY_SERVICE, ConfigConst.PORT_KEY, 6379);
		this.dedupTtlSecs = configUtil.getInteger(
				ConfigConst.REDIS_DATA_GATEWAY_SERVICE, ConfigConst.DEDUP_TTL_SECS_KEY, ConfigConst.DEFAULT_TTL);

		_Logger.info(
				"Redis config - host: " + this.host + ", port: " + this.port + ", dedupTtlSecs: " + this.dedupTtlSecs);
	}

}
