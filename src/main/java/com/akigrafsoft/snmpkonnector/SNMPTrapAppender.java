/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.MDC;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.Snmp;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import org.apache.log4j.Logger;

/**
 * SNMP Trap log4j appender - ideas taken from
 * http://code.google.com/p/log4j-snmp-trap-appender/ which is covered by the
 * Apache License 2.0
 * 
 * The log4j framework is extendible by creating your own appender to sent log4j
 * messages to an alternate destination. See:-
 * 
 * http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html
 * 
 */
public class SNMPTrapAppender extends AppenderSkeleton {

	public static final long TIME_TICKS_MAX = 4294967295L; // 2 ^ 32 - 1

	private final long appenderLoadedTime = System.currentTimeMillis();

	// defaults - can be overridden by properties
	//
	private String managementHost = "127.0.0.1";
	private int managementHostTrapListenPort = 162;
	private String enterpriseOID = "1.3.6.1.2.1.2.0";
	private String applicationTrapOID = "1.3.6.1.2.1.2.0.0.0.0";
	private String communityString = "public";
	private int trapVersion = 2;
	private Snmp snmp;
	private String systemDescription = "akigrafsoft";

	static long current = System.currentTimeMillis();

	static Logger logger = Logger.getLogger(SNMPTrapAppender.class.getName());

	/**
	 * Constructor
	 */
	public SNMPTrapAppender() {
		super();
		build();
	}

	/**
	 * Constructor with a log4j layout
	 * 
	 * @param layoutValue
	 *            log4j layout
	 */
	public SNMPTrapAppender(final Layout layoutValue) {
		super.layout = layoutValue;
		build();
	}

	private void build() {
		try {
			snmp = new Snmp(new DefaultUdpTransportMapping());
		} catch (IOException e) {
			logger.error("SNMPTrapAppender|Snmp IOException:" + e.getMessage());
		}
	}

	@Override
	public synchronized void append(LoggingEvent event) {

		// skip if not severe enough
		//
		if (!isAsSevereAsThreshold(event.getLevel())) {
			return;
		}

		// raise error if no layout
		//
		if (null == getLayout()) {
			errorHandler.error(new StringBuffer().append("No layout set for the Appender named [").append(getName())
					.append(']').toString(), null, ErrorCode.MISSING_LAYOUT);
			return;
		}

		OID oid;
		Object alarmId = MDC.get("alarmId");
		if (alarmId != null && alarmId instanceof Integer) {
			oid = new OID(applicationTrapOID + "." + alarmId);
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"SNMPTrapAppender append() trap not sent because alarmId is null or not an integer for message: "
								+ event.getMessage().toString());
			}
			return;
		}

		// format with the layout
		//
		final PatternLayout pl = (PatternLayout) getLayout();

		// form the trap
		//
		PDU trap;
		if (trapVersion == 1) {
			trap = new PDUv1();
			trap.setType(PDU.V1TRAP);
			((PDUv1) trap).setGenericTrap(PDUv1.ENTERPRISE_SPECIFIC);
		} else {
			trap = new PDU();
			trap.setType(PDU.TRAP);
		}

		MDC.put("alarmNumber", getAlarmNumber());
		MDC.put("datetime", getDateTime(event.getTimeStamp()));
		// alarmType set in client code
		// event source is set as logger name %c
		// endpoint set in client code
		if (event.getLevel().equals(Level.FATAL)) {
			MDC.put("severity", "Critical");
		} else if (event.getLevel().equals(Level.ERROR)) {
			MDC.put("severity", "Major");
		} else if (event.getLevel().equals(Level.WARN)) {
			MDC.put("severity", "Warn");
		} else if (event.getLevel().equals(Level.INFO)) {
			MDC.put("severity", "Cleared");
		}
		// description is set as log message %m

		trap.add(new VariableBinding(SnmpConstants.sysUpTime,
				new TimeTicks((System.currentTimeMillis() - appenderLoadedTime) / 10 % TIME_TICKS_MAX)));
		trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, oid));
		trap.add(new VariableBinding(SnmpConstants.sysDescr, new OctetString(systemDescription)));

		// Add Payload
		//
		Variable var = new OctetString(pl.format(event));
		trap.add(new VariableBinding(oid, var));

		// Specify receiver
		//
		Address targetaddress = new UdpAddress(managementHost + "/" + managementHostTrapListenPort);
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(communityString));
		if (trapVersion == 1) {
			target.setVersion(SnmpConstants.version1);
		} else if (trapVersion == 2) {
			target.setVersion(SnmpConstants.version2c);
		} else {
			// TODO - more needs to be done here to support SNMPv3
			target.setVersion(SnmpConstants.version3);
		}
		target.setAddress(targetaddress);

		// Send
		//
		try {
			snmp.send(trap, target, null, null);
		} catch (Exception e) {
			logger.error("SNMPTrapAppender|send IOException:" + e.getMessage());
		}
	}

	public static synchronized long getAlarmNumber() {
		return current++;
	}

	public static String getDateTime(long timeStamp) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date date = new Date(timeStamp);
		return dateFormat.format(date);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.log4j.AppenderSkeleton#close()
	 */
	@Override
	public void close() {
		if (!closed) {
			closed = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.log4j.AppenderSkeleton#requiresLayout()
	 */
	@Override
	public boolean requiresLayout() {
		return true;
	}

	/**
	 * Get the numeric, dotted-decimal IP address of the remote host that traps
	 * will be sent to, as a String.
	 * 
	 * @return numeric IP address of the trap target
	 */
	public String getManagementHost() {
		return managementHost;
	}

	/**
	 * Set the IP address of the remote host that traps should be sent to.
	 * 
	 * @param managementHostValue
	 *            the IP address of the remote host, in numeric, dotted-decimal
	 *            format, as a String. E.g. "10.255.255.1"
	 */
	public void setManagementHost(final String managementHostValue) {
		managementHost = managementHostValue;
	}

	/**
	 * Get the port used on the remote host to listen for SNMP traps. The
	 * standard is 162.
	 * 
	 * @return target trap port
	 */
	public int getManagementHostTrapListenPort() {
		return managementHostTrapListenPort;
	}

	/**
	 * Set the port used on the remote host to listen for SNMP traps. The
	 * standard is 162.
	 * 
	 * @param managementHostTrapListenPortValue
	 *            any valid TCP/IP port
	 */
	public void setManagementHostTrapListenPort(final int managementHostTrapListenPortValue) {
		managementHostTrapListenPort = managementHostTrapListenPortValue;
	}

	/**
	 * Get the enterprise OID that will be sent in the SNMP PDU.
	 * 
	 * @return A String, formatted as an OID E.g. "1.3.6.1.2.1.1.2.0" -- this
	 *         OID would point to the standard sysObjectID of the "systemName"
	 *         node of the standard "system" MIB.
	 */
	public String getEnterpriseOID() {
		return enterpriseOID;
	}

	/**
	 * Set the enterprise OID that will be sent in the SNMP PDU.
	 * 
	 * @param enterpriseOIDValue
	 *            formatted as an OID E.g. "1.3.6.1.2.1.1.2.0" -- this OID would
	 *            point to the standard sysObjectID of the "systemName" node of
	 *            the standard "system" MIB.
	 *            <p>
	 *            This is the default value, if none is provided.
	 *            </p>
	 *            If you want(need) to use custom OIDs (such as ones from the
	 *            "private.enterprises" node -- "1.3.6.1.4.1.x.x.x..."), you
	 *            always need to provide the <b>fully qualified</b> OID as the
	 *            parameter to this method.
	 */
	public void setEnterpriseOID(final String enterpriseOIDValue) {
		enterpriseOID = enterpriseOIDValue;
	}

	/**
	 * Get the trap OID that will be sent in the SNMP PDU for this app.
	 * 
	 * @return application OID currently set
	 */
	public String getApplicationTrapOID() {
		return applicationTrapOID;
	}

	/**
	 * Set the trap OID that will be sent in the SNMP PDU for this app.
	 * 
	 * @param applicationTrapOIDValue
	 *            formatted as an OID E.g. "1.3.6.1.2.1.2.0.0.0.0" -- this OID
	 *            would point to the standard sysObjectID of the "systemName"
	 *            node of the standard "system" MIB.
	 *            <p>
	 *            This is the default value, if none is provided.
	 *            </p>
	 *            If you want(need) to use custom OIDs (such as ones from the
	 *            "private.enterprises" node -- "1.3.6.1.4.1.x.x.x..."), you
	 *            always need to provide the <b>fully qualified</b> OID as the
	 *            parameter to this method.
	 */
	public void setApplicationTrapOID(final String applicationTrapOIDValue) {
		applicationTrapOID = applicationTrapOIDValue;
	}

	/**
	 * Get the community string set for the SNMP session this appender will use.
	 * 
	 * @return the current community string
	 */
	public String getCommunityString() {
		return communityString;
	}

	/**
	 * Set the community string set for the SNMP session this appender will use.
	 * The community string is used by SNMP (prior to v.3) as a sort of
	 * plain-text password.
	 * 
	 * @param communityStringValue
	 *            E.g. "public". This is the default, if none is provided.
	 */
	public void setCommunityString(final String communityStringValue) {
		communityString = communityStringValue;
	}

	/**
	 * Get the trap version. 1, 2 or 3
	 * 
	 * @return trap version
	 */
	public int getTrapVersion() {
		return trapVersion;
	}

	/**
	 * Set the trap version
	 * 
	 * @param trapVersion
	 *            1, 2 or 3
	 */
	public void setTrapVersion(final int trapVersion) {
		this.trapVersion = trapVersion;
	}

	/**
	 * Get the system description
	 * 
	 * @return system description
	 */
	public String getSystemDescription() {
		return systemDescription;
	}

	/**
	 * Set the system description
	 * 
	 * @param systemDescription
	 *            system description
	 */
	public void setSystemDescription(final String systemDescription) {
		this.systemDescription = systemDescription;
	}
}