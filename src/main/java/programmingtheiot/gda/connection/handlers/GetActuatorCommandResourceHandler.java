package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import okhttp3.internal.Util;
import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;

public class GetActuatorCommandResourceHandler extends CoapResource implements IActuatorDataListener {

    private static final Logger _Logger = Logger.getLogger(GetActuatorCommandResourceHandler.class.getName());

    private ActuatorData actuatorData = null;

    public GetActuatorCommandResourceHandler(String resourceName) {
        super(resourceName);
        super.setObservable(true);
    }

    @Override
    public void handleGET(CoapExchange context) {
        ResponseCode code = ResponseCode.NOT_ACCEPTABLE;

        context.accept();

        String payload = DataUtil.getInstance().actuatorDataToJson(this.actuatorData);

        if (payload != null) {
            code = ResponseCode.CONTENT;
            _Logger.fine("Actuator command data for URI: " + super.getURI());

        }else{
            payload = "No actuator command data available for URI: " + super.getURI();
            code = ResponseCode.NOT_FOUND;
            _Logger.warning(payload);
        }

        context.respond(code, payload);
    }

    @Override
    public boolean onActuatorDataUpdate(ActuatorData data) {
        if (data != null && this.actuatorData != null) {
            this.actuatorData.updateData(data);

            // Californium framework uses observer pattern to notify all connected clients
            super.changed();

            _Logger.fine("Actuator data updated for URI: " + super.getURI() + ": Data value = "
                    + this.actuatorData.getValue());

            return true;
        }

        return false;

    }



}
