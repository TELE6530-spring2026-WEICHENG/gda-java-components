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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.UdpConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GetActuatorCommandResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateTelemetryResourceHandler;

/**
 * Shell representation of class for student implementation.
 *
 */

public class CoapServerGateway {
	// static

	static {
		CoapConfig.register();
		UdpConfig.register();
	}

	private static final Logger _Logger = Logger.getLogger(CoapServerGateway.class.getName());

	// params

	private CoapServer coapServer = null;

	private IDataMessageListener dataMsgListener = null;

	// constructors

	/**
	 * Constructor.
	 *
	 * @param dataMsgListener
	 */
	public CoapServerGateway(IDataMessageListener dataMsgListener) {
		super();

		this.dataMsgListener = dataMsgListener;

		initServer();
	}

	// public methods

	public void addResource(ResourceNameEnum resourceType, String endName, Resource resource) {
		// TODO: endName is reserved for future use — when provided, it should override
		// the final segment of resourceType's name chain, enabling multiple instances
		// of the same ResourceNameEnum to be registered under different endpoint names
		// (e.g., TempSensor, HumiditySensor both under CDA_SENSOR_MSG_RESOURCE).
		if (resourceType != null && resource != null) {
			createAndAddResourceChain(resourceType, resource);
		}
	}

	public boolean hasResource(String name) {
		if (name != null && !name.isEmpty() && this.coapServer != null) {
			String[] segments = name.split("/");
			Resource current = this.coapServer.getRoot();

			for (String segment : segments) {
				current = current.getChild(segment);

				if (current == null) {
					return false;
				}
			}

			return true;
		}

		return false;
	}

	public void setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}

	public boolean startServer() {
		if (this.coapServer != null) {
			try {
				_Logger.info("Starting CoAP server...");
				this.coapServer.start();
				return true;
			} catch (Exception e) {
				_Logger.log(Level.SEVERE, "Failed to start CoAP server.", e);
			}
		}

		return false;
	}

	public boolean stopServer() {
		if (this.coapServer != null) {
			try {
				_Logger.info("Stopping CoAP server...");
				this.coapServer.stop();
				return true;
			} catch (Exception e) {
				_Logger.log(Level.SEVERE, "Failed to stop CoAP server.", e);
			}
		}

		return false;
	}

	// private methods

	private void createAndAddResourceChain(ResourceNameEnum resourceType, Resource resource) {
		_Logger.info("Adding server resource handler chain: " + resourceType.getResourceName());

		List<String> resourceNames = resourceType.getResourceNameChain();
		Queue<String> queue = new ArrayBlockingQueue<>(resourceNames.size());
		queue.addAll(resourceNames);

		Resource parentResource = this.coapServer.getRoot();

		while (!queue.isEmpty()) {
			String resourceName = queue.poll();
			Resource nextResource = parentResource.getChild(resourceName);

			if (nextResource == null) {
				if (queue.isEmpty()) {
					// last segment — attach the actual handler
					nextResource = resource;
					nextResource.setName(resourceName);
				} else {
					// intermediate segment — plain path placeholder
					nextResource = new CoapResource(resourceName);
				}
				parentResource.add(nextResource);
			}

			parentResource = nextResource;
		}
	}

	private void initDefaultResources() {
		// 1. GetActuatorCommandResourceHandler — CDA observes this to receive commands
		GetActuatorCommandResourceHandler getActuatorCmdHandler = new GetActuatorCommandResourceHandler(
				ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE.getResourceType()); // 'ActuatorCmd'

		if (this.dataMsgListener != null) {
			this.dataMsgListener.setActuatorDataListener(null, getActuatorCmdHandler);
		}

		addResource(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, null, getActuatorCmdHandler);

		// 2. UpdateTelemetryResourceHandler — CDA PUT sensor data
		UpdateTelemetryResourceHandler updateTelemetryHandler = new UpdateTelemetryResourceHandler(
				ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceType());

		updateTelemetryHandler.setDataMessageListener(this.dataMsgListener);
		addResource(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, null, updateTelemetryHandler);

		// 3. UpdateSystemPerformanceResourceHandler — CDA PUT system performance data
		UpdateSystemPerformanceResourceHandler updateSysPerfHandler = new UpdateSystemPerformanceResourceHandler(
				ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceType());

		updateSysPerfHandler.setDataMessageListener(this.dataMsgListener);
		addResource(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, null, updateSysPerfHandler);
	}

	private void initServer(ResourceNameEnum... resources) {
		int port = ConfigUtil.getInstance().getInteger(
				ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_COAP_PORT);

		this.coapServer = new CoapServer(port);
		_Logger.info("Created CoAP server on port " + port);

		initDefaultResources();
	}
}
