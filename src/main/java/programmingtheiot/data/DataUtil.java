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

package programmingtheiot.data;

import java.util.logging.Logger;

import com.google.gson.Gson;

public class DataUtil
{
	// static

	private static final Logger _Logger =
		Logger.getLogger(DataUtil.class.getName());

	private static final DataUtil _Instance = new DataUtil();

	public static final DataUtil getInstance()
	{
		return _Instance;
	}


	// constructors

	private DataUtil()
	{
		super();
	}


	// public methods

	public String actuatorDataToJson(ActuatorData actuatorData)
	{
		if (actuatorData != null) {
			Gson gson = new Gson();
			return gson.toJson(actuatorData);
		}
		return null;
	}

	public String actuatorDataToTimeAndValueJson(ActuatorData actuatorData)
	{
		if (actuatorData != null) {
			TimeAndValuePayloadData tvData = new TimeAndValuePayloadData(actuatorData);
			Gson gson = new Gson();
			return gson.toJson(tvData);
		}
		return null;
	}

	public String sensorDataToJson(SensorData sensorData)
	{
		if (sensorData != null) {
			Gson gson = new Gson();
			return gson.toJson(sensorData);
		}
		return null;
	}

	public String sensorDataToTimeAndValueJson(SensorData sensorData)
	{
		if (sensorData != null) {
			TimeAndValuePayloadData tvData = new TimeAndValuePayloadData(sensorData);
			Gson gson = new Gson();
			return gson.toJson(tvData);
		}
		return null;
	}

	public String systemPerformanceDataToJson(SystemPerformanceData sysPerfData)
	{
		if (sysPerfData != null) {
			Gson gson = new Gson();
			return gson.toJson(sysPerfData);
		}
		return null;
	}

	public String systemStateDataToJson(SystemStateData sysStateData)
	{
		if (sysStateData != null) {
			Gson gson = new Gson();
			return gson.toJson(sysStateData);
		}
		return null;
	}

	public ActuatorData jsonToActuatorData(String jsonData)
	{
		if (jsonData != null && jsonData.trim().length() > 0) {
			Gson gson = new Gson();
			return gson.fromJson(jsonData, ActuatorData.class);
		}
		return null;
	}

	public SensorData jsonToSensorData(String jsonData)
	{
		if (jsonData != null && jsonData.trim().length() > 0) {
			Gson gson = new Gson();
			return gson.fromJson(jsonData, SensorData.class);
		}
		return null;
	}

	public SystemPerformanceData jsonToSystemPerformanceData(String jsonData)
	{
		if (jsonData != null && jsonData.trim().length() > 0) {
			Gson gson = new Gson();
			return gson.fromJson(jsonData, SystemPerformanceData.class);
		}
		return null;
	}

	public SystemStateData jsonToSystemStateData(String jsonData)
	{
		if (jsonData != null && jsonData.trim().length() > 0) {
			Gson gson = new Gson();
			return gson.fromJson(jsonData, SystemStateData.class);
		}
		return null;
	}

}
