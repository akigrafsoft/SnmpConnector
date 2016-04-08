/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import java.io.IOException;

import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OctetString;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.akigrafsoft.knetthreads.ExceptionDuplicate;
import com.akigrafsoft.knetthreads.konnector.ExceptionCreateSessionFailed;
import com.akigrafsoft.knetthreads.konnector.KonnectorConfiguration;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject.SyncMode;
import com.akigrafsoft.knetthreads.konnector.SessionBasedClientKonnector;

public class SnmpClientKonnector extends SessionBasedClientKonnector {

	private Protocol m_protocol = Protocol.TCP;

	public SnmpClientKonnector(String name) throws ExceptionDuplicate {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Class<? extends KonnectorConfiguration> getConfigurationClass() {
		return SnmpClientKonnectorConfig.class;
	}

	@Override
	protected void doLoadConfig(KonnectorConfiguration config) {
		super.doLoadConfig(config);
		SnmpClientKonnectorConfig l_cfg = (SnmpClientKonnectorConfig) config;

		USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
		SecurityModels.getInstance().addSecurityModel(usm);

		m_protocol = l_cfg.getProtocol();
	}

	@SuppressWarnings("rawtypes")
	private static class SnmpSession {

		private final TransportMapping transport;
		private final Snmp snmp;

		SnmpSession(TransportMapping transport, Snmp snmp) {
			this.transport = transport;
			this.snmp = snmp;
		}

		public TransportMapping getTransport() {
			return transport;
		}

		public Snmp getSnmp() {
			return snmp;
		}

	}

	@Override
	protected void createSession(Session session) throws ExceptionCreateSessionFailed {
		@SuppressWarnings("rawtypes")
		TransportMapping transport;
		try {
			// transport = new DefaultUdpTransportMapping();
			if (Protocol.TCP.equals(m_protocol)) {
				// transport = new DefaultTcpTransportMapping((TcpAddress)
				// m_targetAddress);
				transport = new DefaultTcpTransportMapping();
			} else {
				// transport = new DefaultUdpTransportMapping((UdpAddress)
				// m_targetAddress);
				transport = new DefaultUdpTransportMapping();
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new ExceptionCreateSessionFailed("IOException:" + e.getMessage());
		}

		@SuppressWarnings("unchecked")
		Snmp snmp = new Snmp(transport);

		// SNMPv3 auth, add user to the USM
		//
		// snmp.getUSM().addUser(new OctetString("MD5DES"), new UsmUser(new
		// OctetString("MD5DES"), AuthMD5.ID,
		// new OctetString("MD5DESUserAuthPassword"), PrivDES.ID, new
		// OctetString("MD5DESUserPrivPassword")));

		session.setUserObject(new SnmpSession(transport, snmp));
	}

	@Override
	public void async_startSession(Session session) {
		SnmpSession l_session = (SnmpSession) session.getUserObject();

		try {
			l_session.getTransport().listen();
		} catch (IOException e) {
			e.printStackTrace();
			this.sessionDied(session, "IOException:" + e.getMessage());
			return;
		}
		this.sessionStarted(session);
	}

	@Override
	protected void execute(final KonnectorDataobject dataobject, final Session session) {
		SnmpDataobject l_dataobject = (SnmpDataobject) dataobject;
		SnmpSession l_session = (SnmpSession) session.getUserObject();

		if (SyncMode.ASYNC.equals(dataobject.operationSyncMode)) {
			try {
				l_session.getSnmp().send(l_dataobject.getPdu(), l_dataobject.getCommunityTarget(),
						l_session.getTransport(), null, new ResponseListener() {
							public void onResponse(ResponseEvent event) {
								// Always cancel async request when response has
								// been received otherwise a memory leak is
								// created!
								// Not canceling a request immediately can be
								// useful
								// when sending a request to a broadcast
								// address.
								((Snmp) event.getSource()).cancel(event.getRequest(), this);

								// AdminLogger
								// .debug(buildAdminLog("onResponse()|Received
								// PDU<"
								// + event.getRequest() + ">"));

								if (AdminLogger.isDebugEnabled())
									AdminLogger.debug(
											buildAdminLog("onResponse()|Received PDU<" + event.getResponse() + ">"));

								l_dataobject.setResponsePdu(event.getResponse());

								notifyExecuteCompleted(dataobject);
							}
						});
			} catch (IOException e) {
				e.printStackTrace();
				notifyNetworkError(dataobject, session, "IOException:" + e.getMessage());
			}
		} else {
			try {
				ResponseEvent event = l_session.getSnmp().send(l_dataobject.getPdu(), l_dataobject.getCommunityTarget(),
						l_session.getTransport());
				if (AdminLogger.isDebugEnabled())
					AdminLogger.debug(buildAdminLog("onResponse()|Received PDU<" + event.getResponse() + ">"));
				l_dataobject.setResponsePdu(event.getResponse());
				notifyExecuteCompleted(dataobject);
			} catch (IOException e) {
				e.printStackTrace();
				notifyNetworkError(dataobject, session, "IOException:" + e.getMessage());
			}
		}

	}

	@Override
	protected void async_stopSession(Session session) {
		SnmpSession l_session = (SnmpSession) session.getUserObject();
		try {
			l_session.getTransport().close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			sessionStopped(session);
		}
	}

}
