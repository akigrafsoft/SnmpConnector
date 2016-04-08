/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;

import com.akigrafsoft.knetthreads.Message;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;

public class SnmpDataobject extends KonnectorDataobject {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1202763357760818627L;

	private PDU pdu;
	private PDU responsePdu;

	private CommunityTarget communityTarget;

	private CommandResponderEvent cmdRespEvent;

	public SnmpDataobject(Message message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public PDU getPdu() {
		return pdu;
	}

	public void setPdu(PDU pdu) {
		this.pdu = pdu;
	}

	public PDU getResponsePdu() {
		return responsePdu;
	}

	public void setResponsePdu(PDU responsePdu) {
		this.responsePdu = responsePdu;
	}

	public void setCommunityTarget(CommunityTarget target) {
		this.communityTarget = target;
	}

	public CommunityTarget getCommunityTarget() {
		return communityTarget;
	}

	public CommandResponderEvent getCmdRespEvent() {
		return cmdRespEvent;
	}

	public void setCmdRespEvent(CommandResponderEvent cmdRespEvent) {
		this.cmdRespEvent = cmdRespEvent;
	}

}
