/**
 * This class is part of the Programming the Internet of Things project.
 *
 * Payload embedded inside ActuatorData.stateData when the CDA runs its
 * autonomous water-pump irrigation session. The CDA serializes this as
 * JSON via Python's json.dumps; the GDA deserializes it via GSON.
 *
 * Phase values: OPENED, PROGRESS, COMPLETED, ABORTED (see ConfigConst).
 * "reason" is only populated when phase == ABORTED.
 */

package programmingtheiot.data;

import java.io.Serializable;

public class IrrigationStatePayload implements Serializable
{
	private String phase;
	private String sessionId;
	private int pulseCount;
	private float currentMoisture;
	private float targetMoisture;
	private String reason;

	public IrrigationStatePayload()
	{
		super();
	}

	public String getPhase()
	{
		return this.phase;
	}

	public void setPhase(String phase)
	{
		this.phase = phase;
	}

	public String getSessionId()
	{
		return this.sessionId;
	}

	public void setSessionId(String sessionId)
	{
		this.sessionId = sessionId;
	}

	public int getPulseCount()
	{
		return this.pulseCount;
	}

	public void setPulseCount(int pulseCount)
	{
		this.pulseCount = pulseCount;
	}

	public float getCurrentMoisture()
	{
		return this.currentMoisture;
	}

	public void setCurrentMoisture(float currentMoisture)
	{
		this.currentMoisture = currentMoisture;
	}

	public float getTargetMoisture()
	{
		return this.targetMoisture;
	}

	public void setTargetMoisture(float targetMoisture)
	{
		this.targetMoisture = targetMoisture;
	}

	public String getReason()
	{
		return this.reason;
	}

	public void setReason(String reason)
	{
		this.reason = reason;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("phase=").append(this.phase);
		sb.append(",sessionId=").append(this.sessionId);
		sb.append(",pulseCount=").append(this.pulseCount);
		sb.append(",currentMoisture=").append(this.currentMoisture);
		sb.append(",targetMoisture=").append(this.targetMoisture);
		sb.append(",reason=").append(this.reason);
		return sb.toString();
	}
}
