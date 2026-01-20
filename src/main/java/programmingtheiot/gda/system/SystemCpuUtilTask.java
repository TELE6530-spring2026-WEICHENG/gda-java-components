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

package programmingtheiot.gda.system;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.logging.Logger;
import programmingtheiot.common.ConfigConst;


/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemCpuUtilTask extends BaseSystemUtilTask {

	private static final Logger _Logger = Logger.getLogger(SystemCpuUtilTask.class.getName());
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public SystemCpuUtilTask() {
		super(ConfigConst.NOT_SET, ConfigConst.DEFAULT_TYPE_ID);
	}
	
	
	// public methods
	
	@Override
	public float getTelemetryValue() {
		// Get the number of available CUP
		int cpuCount = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
		_Logger.info("Available CPU Count: " + cpuCount);

		OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
		double loadAvg = operatingSystemMXBean.getSystemLoadAverage();
		_Logger.info("System Load Average: " + loadAvg);

		if (loadAvg < 0.0) {
			_Logger.info("CPU loadAvg unsupported");
			return 0.0f;
		}
		double workLoadPressure = loadAvg / cpuCount;

		int level = (workLoadPressure < 0.70) ? 0
				: (workLoadPressure <= 1.00) ? 1
				: (workLoadPressure <= 1.50) ? 2
				: 3;

		switch (level) {
			case 0:
				_Logger.info("CPU is not busy");
			case 1:
				_Logger.info("CPU is busy");
			case 2:
				_Logger.info("CPU is saturated");
			case 3:
				_Logger.info("CPU is overloaded");
		}
		return (float) loadAvg;
	}
	
}
