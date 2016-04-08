/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import java.io.IOException;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.log.LogFactory;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.tools.console.SnmpRequest;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import com.akigrafsoft.knetthreads.ExceptionDuplicate;
import com.akigrafsoft.knetthreads.Message;
import com.akigrafsoft.knetthreads.konnector.Konnector;
import com.akigrafsoft.knetthreads.konnector.KonnectorConfiguration;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;

public class SnmpServerKonnector extends Konnector implements CommandResponder {

	private TransportIpAddress m_address = null;

	private Snmp m_snmp = null;

	@SuppressWarnings("rawtypes")
	private AbstractTransportMapping transport;

	public SnmpServerKonnector(String name) throws ExceptionDuplicate {
		super(name);
	}

	@Override
	public Class<? extends KonnectorConfiguration> getConfigurationClass() {
		return SnmpServerKonnectorConfig.class;
	}

	@Override
	protected void doLoadConfig(KonnectorConfiguration config) {
		super.doLoadConfig(config);
		SnmpServerKonnectorConfig l_cfg = (SnmpServerKonnectorConfig) config;

		// Audited, so it should be correct
		m_address = (TransportIpAddress) GenericAddress.parse(l_cfg.getGenericAddress());
	}

	@Override
	protected CommandResult doStart() {
		try {
			// this.listen(new UdpAddress(InetAddress.getByName(host), port));
			this.listen(m_address);
		} catch (IOException e) {
			AdminLogger.info(buildAdminLog("doStart()|IOException" + e.getMessage()));
			e.printStackTrace();
			return CommandResult.Fail;
		}

		this.setStarted();

		return CommandResult.Success;
	}

	/**
	 * This method will listen for traps and response pdu's from SNMP agent.
	 * 
	 * @param address
	 *            TransportIpAddress to listen on
	 * @throws IOException
	 *             if listen() failed
	 */
	@SuppressWarnings("unchecked")
	public synchronized void listen(TransportIpAddress address) throws IOException {
		if (address instanceof TcpAddress) {
			transport = new DefaultTcpTransportMapping((TcpAddress) address);
		} else {
			transport = new DefaultUdpTransportMapping((UdpAddress) address);
		}

		ThreadPool threadPool = ThreadPool.create("DispatcherPool", 10);
		MessageDispatcher mtDispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());

		// add message processing models
		mtDispatcher.addMessageProcessingModel(new MPv1());
		mtDispatcher.addMessageProcessingModel(new MPv2c());
		mtDispatcher.addMessageProcessingModel(new MPv3());

		// add all security protocols
		SecurityProtocols.getInstance().addDefaultProtocols();
		SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());

		// Create Target
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));

		m_snmp = new Snmp(mtDispatcher, transport);
		m_snmp.addCommandResponder(this);

		transport.listen();

		AdminLogger.info(buildAdminLog("Listening on " + address));
	}

	/**
	 * This method will be called whenever a pdu is received on the given port
	 * specified in the listen() method
	 */
	@Override
	public void processPdu(CommandResponderEvent cmdRespEvent) {
		PDU pdu = cmdRespEvent.getPDU();
		if (AdminLogger.isDebugEnabled())
			AdminLogger.debug(buildAdminLog("processPdu()|received PDU<" + pdu + ">"));

		if (pdu == null)
			return;

		int pduType = pdu.getType();

		Message message = new Message();
		SnmpDataobject l_dataobject = new SnmpDataobject(message);
		l_dataobject.setPdu(pdu);
		l_dataobject.setCmdRespEvent(cmdRespEvent);

		injectMessageInApplication(message, l_dataobject);

		if ((pduType != PDU.TRAP) && (pduType != PDU.V1TRAP) && (pduType != PDU.REPORT) && (pduType != PDU.RESPONSE)) {
			pdu.setErrorIndex(0);
			pdu.setErrorStatus(0);
			pdu.setType(PDU.RESPONSE);
			StatusInformation statusInformation = new StatusInformation();
			StateReference ref = cmdRespEvent.getStateReference();
			try {
				if (AdminLogger.isDebugEnabled())
					AdminLogger.debug(buildAdminLog("processPdu()|responding with PDU<" + cmdRespEvent.getPDU() + ">"));
				cmdRespEvent.setPDU(pdu);
				cmdRespEvent.getMessageDispatcher().returnResponsePdu(cmdRespEvent.getMessageProcessingModel(),
						cmdRespEvent.getSecurityModel(), cmdRespEvent.getSecurityName(),
						cmdRespEvent.getSecurityLevel(), pdu, cmdRespEvent.getMaxSizeResponsePDU(), ref,
						statusInformation);
			} catch (MessageException ex) {
				AdminLogger.error(buildAdminLog("Error while sending response: " + ex.getMessage()));
				LogFactory.getLogger(SnmpRequest.class).error(ex);
			}
		}
	}

	@Override
	public void doHandle(KonnectorDataobject dataobject) {
		SnmpDataobject l_dataobject = (SnmpDataobject) dataobject;

		CommandResponderEvent cmdRespEvent = l_dataobject.getCmdRespEvent();

		if (cmdRespEvent == null) {
			// this.resumeWithFunctionalError(dataobject, "cmdRespEvent ==
			// null");
			handleOutbound(l_dataobject);
			return;
		}

		StatusInformation statusInformation = new StatusInformation();
		StateReference ref = cmdRespEvent.getStateReference();
		try {
			if (AdminLogger.isDebugEnabled())
				AdminLogger.debug(buildAdminLog("doHandle()|send PDU<" + l_dataobject.getPdu() + ">"));
			cmdRespEvent.getMessageDispatcher().returnResponsePdu(cmdRespEvent.getMessageProcessingModel(),
					cmdRespEvent.getSecurityModel(), cmdRespEvent.getSecurityName(), cmdRespEvent.getSecurityLevel(),
					l_dataobject.getPdu(), cmdRespEvent.getMaxSizeResponsePDU(), ref, statusInformation);
		} catch (MessageException e) {
			AdminLogger.error("doHandle()|MessageException: " + e.getMessage());
			e.printStackTrace();
			this.resumeWithNetworkError(dataobject, e.getMessage());
			return;
		}

		this.resumeWithExecutionComplete(dataobject);
	}

	private void handleOutbound(SnmpDataobject dataobject) {
		try {
			m_snmp.send(dataobject.getPdu(), dataobject.getCommunityTarget(), transport, null, new ResponseListener() {
				public void onResponse(ResponseEvent event) {
					// Always cancel async request when response has
					// been received otherwise a memory leak is created!
					// Not canceling a request immediately can be useful
					// when sending a request to a broadcast address.
					((Snmp) event.getSource()).cancel(event.getRequest(), this);
					System.out.println("Received response PDU is: " + event.getResponse());

					resumeWithExecutionComplete(dataobject);
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
			resumeWithNetworkError(dataobject, "IOException:" + e.getMessage());
		}
	}

	@Override
	protected CommandResult doStop() {
		if (transport != null) {
			try {
				transport.close();
			} catch (IOException e) {
				e.printStackTrace();
				return CommandResult.Fail;
			}
			transport = null;
		}
		this.setStopped();
		return CommandResult.Success;
	}

}