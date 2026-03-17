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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.UdpConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GenericCoapResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;

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

		/*
		 * Basic constructor implementation provided. Change as needed.
		 */

		this.dataMsgListener = dataMsgListener;

		// Initialize the CoAP server and add resources to it here.

		initServer();
	}

	// public methods

	public void addResource(ResourceNameEnum resource) {
		if (resource != null) {
			_Logger.info("Adding server resource handler chain: " + resource.getResourceName());

			createResourceChain(resource);
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

	private Resource createResourceChain(ResourceNameEnum resource) {
		List<String> nameChain = resource.getResourceNameChain();
		Resource current = this.coapServer.getRoot();

		for (int i = 0; i < nameChain.size(); i++) {
			String segment = nameChain.get(i);
			Resource child = current.getChild(segment);

			if (child == null) {
				if (i == nameChain.size() - 1) {
					// last segment — attach the actual handler
					if (resource == ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE) {
						UpdateSystemPerformanceResourceHandler perfHandler =
							new UpdateSystemPerformanceResourceHandler(segment);
						perfHandler.setDataMessageListener(this.dataMsgListener);

						if (resource.isObservable()) {
							perfHandler.setObservable(true);
							perfHandler.getAttributes().setObservable();
						}

						current.add(perfHandler);
						current = perfHandler;
					} else {
						GenericCoapResourceHandler handler = new GenericCoapResourceHandler(segment);
						handler.setDataMessageListener(this.dataMsgListener);

						if (resource.isObservable()) {
							handler.setObservable(true);
							handler.getAttributes().setObservable();
						}

						current.add(handler);
						current = handler;
					}
				} else {
					// intermediate segment — plain CoapResource placeholder
					CoapResource placeholder = new CoapResource(segment);
					current.add(placeholder);
					current = placeholder;
				}
			} else {
				current = child;
			}
		}

		return current;
	}

	private void initServer(ResourceNameEnum... resources) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		int port = configUtil.getInteger(
			ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_COAP_PORT);

		this.coapServer = new CoapServer(port);

		_Logger.info("Created CoAP server on port " + port);

		// if no specific resources provided, register all ResourceNameEnum values
		if (resources == null || resources.length == 0) {
			resources = ResourceNameEnum.values();
		}

		for (ResourceNameEnum resource : resources) {
			addResource(resource);
		}
	}
}
