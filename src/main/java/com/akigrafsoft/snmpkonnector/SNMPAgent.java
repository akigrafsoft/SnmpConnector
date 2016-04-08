/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import java.io.File;

import org.snmp4j.agent.BaseAgent;
import org.snmp4j.agent.CommandProcessor;
import org.snmp4j.agent.mo.snmp.SnmpCommunityMIB;
import org.snmp4j.agent.mo.snmp.SnmpNotificationMIB;
import org.snmp4j.agent.mo.snmp.SnmpTargetMIB;
import org.snmp4j.agent.mo.snmp.VacmMIB;
import org.snmp4j.security.USM;

public class SNMPAgent extends BaseAgent {

	protected SNMPAgent(String configURI) {
		super(configURI);
		// TODO Auto-generated constructor stub
	}

	protected SNMPAgent(File bootCounterFile, File configFile, CommandProcessor commandProcessor) {
		super(bootCounterFile, configFile, commandProcessor);
	}

	@Override
	protected void addCommunities(SnmpCommunityMIB arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addNotificationTargets(SnmpTargetMIB arg0, SnmpNotificationMIB arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addUsmUser(USM arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addViews(VacmMIB arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void registerManagedObjects() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void unregisterManagedObjects() {
		// TODO Auto-generated method stub

	}

}
