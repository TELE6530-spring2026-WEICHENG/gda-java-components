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

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;

/**
 * Singleton factory that returns the ICloudClient implementation selected
 * by the `cloudServiceName` key in the [Cloud.GatewayService] section of
 * the config file.
 *
 * Supported values:
 *   - "AWS"     -> AwsIotCoreClientConnector
 *   - "Ubidots" -> CloudClientConnector  (default / fallback)
 */
public class CloudClientFactory
{
	// static

	private static final Logger _Logger = Logger.getLogger(CloudClientFactory.class.getName());

	private static final CloudClientFactory _Instance = new CloudClientFactory();

	public static CloudClientFactory getInstance()
	{
		return _Instance;
	}

	// instance

	private ICloudClient cloudClient = null;

	// constructors

	private CloudClientFactory()
	{
		// singleton
	}

	// public methods

	/**
	 * Returns the cached ICloudClient instance, creating it on first call
	 * based on the `cloudServiceName` config value.
	 */
	public synchronized ICloudClient getCloudClient()
	{
		if (this.cloudClient == null) {
			String cloudSvcName =
				ConfigUtil.getInstance().getProperty(
					ConfigConst.CLOUD_GATEWAY_SERVICE,
					ConfigConst.CLOUD_SERVICE_NAME_KEY,
					ConfigConst.UBIDOTS_CLOUD_SVC_NAME);

			if (ConfigConst.AWS_CLOUD_SVC_NAME.equalsIgnoreCase(cloudSvcName)) {
				_Logger.info("Creating AWS IoT Core cloud client.");
				this.cloudClient = new AwsIotCoreClientConnector();
			} else {
				_Logger.info("Creating Ubidots cloud client (default).");
				this.cloudClient = new CloudClientConnector();
			}
		}

		return this.cloudClient;
	}

}
