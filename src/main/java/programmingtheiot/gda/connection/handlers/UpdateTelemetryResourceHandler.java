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

package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;

public class UpdateTelemetryResourceHandler extends CoapResource {

    private static final Logger _Logger = Logger.getLogger(UpdateTelemetryResourceHandler.class.getName());

    private IDataMessageListener dataMsgListener = null;

    public UpdateTelemetryResourceHandler(String resourceName) {
        super(resourceName);
    }

    public void setDataMessageListener(IDataMessageListener listener) {
        if (listener != null) {
            this.dataMsgListener = listener;
        }
    }

    @Override
    public void handlePUT(CoapExchange context) {
        ResponseCode code = ResponseCode.NOT_ACCEPTABLE;

        context.accept();
        if (this.dataMsgListener != null) {
            try {
                String payload = context.getRequestText();

                SensorData sensorData = DataUtil.getInstance().jsonToSensorData(payload);

                String deviceName = sensorData.getName();
                long tsMillis = sensorData.getTimeStampMillis();

                boolean stored = RedisPersistenceAdapter.getInstance()
                        .storeCoapData(deviceName, tsMillis, sensorData);

                if (!stored) {
                    _Logger.info("Duplicate CoAP data detected for device: " + deviceName
                            + ", ts: " + tsMillis + ". Skipping processing.");
                    code = ResponseCode.CONTINUE;
                    context.respond(code, "Duplicate request ignored: " + super.getName());
                    return;
                }

                this.dataMsgListener.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE,
                        sensorData);

                code = ResponseCode.CHANGED;
            } catch (Exception e) {
                _Logger.severe("Failed to process CoAP PUT request: " + e.getMessage());
                code = ResponseCode.BAD_REQUEST;
            }
        } else {
            _Logger.warning("IDataMessageListener is not set. Ignoring PUT.");
            code = ResponseCode.CONTINUE;
        }

        String msg = "Update telemetry data request handled: " + super.getName();

        context.respond(code, msg);
    }

}
