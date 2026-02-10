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

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemPerformanceManager {

	private static final Logger _Logger = Logger.getLogger(SystemPerformanceManager.class.getName());
	private int pollRate = ConfigConst.DEFAULT_POLL_CYCLES;
	private boolean isStarted = false;

	private Runnable taskRunner = null;
	private ScheduledExecutorService scheduler =null;

	private BaseSystemUtilTask sysCpuUtilTask = null;
	private BaseSystemUtilTask sysMemoryUtilTask = null;


	// constructors
	/**
	 * Default.
	 * 
	 */
	public SystemPerformanceManager() {
		this.pollRate =
				ConfigUtil.getInstance().getInteger(
						ConfigConst.GATEWAY_DEVICE, ConfigConst.POLL_CYCLES_KEY, ConfigConst.DEFAULT_POLL_CYCLES);

		if (this.pollRate <= 0) {
			this.pollRate = ConfigConst.DEFAULT_POLL_CYCLES;
		}

		this.scheduler = Executors.newScheduledThreadPool(1);
		this.sysCpuUtilTask = new SystemCpuUtilTask();
		this.sysMemoryUtilTask = new SystemMemUtilTask();

		this.taskRunner = this::handleTelemetry;

	}
	
	
	// public methods
	
	public void handleTelemetry() {
		float cpuUtil = this.sysCpuUtilTask.getTelemetryValue();
		float memUtil = this.sysMemoryUtilTask.getTelemetryValue();
		_Logger.fine("CPU utilization: " + cpuUtil + ", Mem utilization: " + memUtil);
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
	}
	
	public boolean startManager() {

		if (! this.isStarted) {
			_Logger.info("SystemPerformanceManager is starting...");

			ScheduledFuture<?> futureTask =
					this.scheduler.scheduleAtFixedRate(this.taskRunner, 1L, this.pollRate, TimeUnit.SECONDS);

			this.isStarted = true;
		} else {
			_Logger.info("SystemPerformanceManager is already started.");
		}

		return this.isStarted;

	}
	
	public boolean stopManager() {
		this.scheduler.shutdown();
		this.isStarted = false;

		_Logger.info("SystemPerformanceManager is stopped.");

		return true;
	}
	
}
