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
import java.lang.management.MemoryUsage;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemMemUtilTask extends BaseSystemUtilTask {

	private static final Logger _Logger = Logger.getLogger(BaseSystemUtilTask.class.getName());

	
	/**
	 * Default.
	 * 
	 */
	public SystemMemUtilTask()
	{
		super(ConfigConst.NOT_SET, ConfigConst.DEFAULT_TYPE_ID);
	}
	
	
	// public methods
	
	@Override
	public float getTelemetryValue(){

		MemoryUsage memUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		double memUsed = (double) memUsage.getUsed();
		double memMax  = (double) memUsage.getMax();

		String msg = "Mem used: " + memUsed + "; Mem Max: " + memMax;
		_Logger.info(msg);

		double memUtil = (memUsed / memMax) * 100.0d;

		return (float) memUtil;

	}
	
}
