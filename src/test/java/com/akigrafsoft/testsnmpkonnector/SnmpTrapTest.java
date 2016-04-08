/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.testsnmpkonnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;

import com.akigrafsoft.knetthreads.Dispatcher;
import com.akigrafsoft.knetthreads.Endpoint;
import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.ExceptionDuplicate;
import com.akigrafsoft.knetthreads.FlowProcessContext;
import com.akigrafsoft.knetthreads.Message;
import com.akigrafsoft.knetthreads.RequestEnum;
import com.akigrafsoft.knetthreads.konnector.Konnector.OperationalStatus;
import com.akigrafsoft.knetthreads.konnector.Konnector.Status;
import com.akigrafsoft.knetthreads.konnector.KonnectorController;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject.SyncMode;
import com.akigrafsoft.knetthreads.routing.EndpointRouter;
import com.akigrafsoft.knetthreads.routing.KonnectorRouter;
import com.akigrafsoft.snmpkonnector.Protocol;
import com.akigrafsoft.snmpkonnector.SnmpClientKonnector;
import com.akigrafsoft.snmpkonnector.SnmpClientKonnectorConfig;
import com.akigrafsoft.snmpkonnector.SnmpDataobject;
import com.akigrafsoft.snmpkonnector.SnmpServerKonnector;
import com.akigrafsoft.snmpkonnector.SnmpServerKonnectorConfig;

public class SnmpTrapTest {

	public static String SERVER_NAME = "SNMP_Server";
	public static String SERVER_EP = "SNMP_Endpoint";

	public static String CLIENT_NAME = "SNMP_Client";

	static long startTime = System.nanoTime();

	private Logger logger = Logger.getLogger(SnmpTrapTest.class.getName());

	// Konnector m_serverKonnector;

	static volatile boolean received_trap = false;

	@BeforeClass
	public static void setUpClass() {

		// Server
		try {
			new SnmpServerKonnector(SERVER_NAME);
		} catch (ExceptionDuplicate e) {
			e.printStackTrace();
			fail(e.getMessage());
			return;
		}

		try {
			SnmpServerKonnectorConfig config = new SnmpServerKonnectorConfig();
			config.setGenericAddress("udp:localhost/7162");
			KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).configure(config);
		} catch (ExceptionAuditFailed e) {
			e.printStackTrace();
			fail("ExceptionAuditFailed:" + e.getMessage());
			return;
		}

		// Client
		try {
			new SnmpClientKonnector(CLIENT_NAME);
		} catch (ExceptionDuplicate e) {
			e.printStackTrace();
			fail(e.getMessage());
			return;
		}

		try {
			SnmpClientKonnectorConfig config = new SnmpClientKonnectorConfig();
			config.setProtocol(Protocol.UDP);
			config.setNumberOfSessions(2);
			KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).configure(config);
		} catch (ExceptionAuditFailed e) {
			e.printStackTrace();
			fail("ExceptionAuditFailed:" + e.getMessage());
			return;
		}

		try {
			final Endpoint l_ep = new Endpoint(SERVER_EP) {
				@Override
				public KonnectorRouter getKonnectorRouter(Message message, KonnectorDataobject dataobject) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public RequestEnum classifyInboundMessage(Message message, KonnectorDataobject dataobject) {
					SnmpDataobject l_do = (SnmpDataobject) dataobject;

					if (PDU.V1TRAP == l_do.getPdu().getType())
						received_trap = true;

					System.out.println("received<" + l_do.getPdu() + ">");
					return null;
				}
			};
			l_ep.setDispatcher(new Dispatcher<RequestEnum>("foo") {
				@Override
				public FlowProcessContext getContext(Message message, KonnectorDataobject dataobject,
						RequestEnum request) {
					// TODO Auto-generated method stub
					return null;
				}
			});

			KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).setEndpointRouter(new EndpointRouter() {
				@Override
				public Endpoint resolveKonnector(Message message, KonnectorDataobject dataobject) {
					return l_ep;
				}
			});
		} catch (ExceptionDuplicate e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).start();
		assertEquals(Status.Started, KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).getStatus());

		KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).start();
		assertEquals(Status.Started, KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).getStatus());

		sleep(100);
		assertEquals(OperationalStatus.Available,
				KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).getOperationalStatus());
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).stop();
		sleep(100);
		assertEquals(Status.Stopped, KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).getStatus());
		KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).destroy();

		KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).stop();
		sleep(100);
		assertEquals(Status.Stopped, KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).getStatus());
		KonnectorController.INSTANCE.getKonnectorByName(SERVER_NAME).destroy();
	}

	@Test
	public void testLog4jTrap() {

		// TODO : test with our own implementation of SNMPappender (see in
		// log4j.properties) instead of joes one.
		// Then joes one can be removed from dependencies
		
		System.out.println("INFO: testLog4jTrap()");

		// test with log4j trap sender
		logger.info("TEST");

		sleep(500);

		assertTrue(received_trap);
	}

	@Test
	public void testInform() {

		System.out.println("INFO: testInform()");

		SnmpDataobject dataobject = new SnmpDataobject(null);
		dataobject.operationSyncMode = SyncMode.SYNC;

		PDU pdu = new PDU();
		pdu.setType(PDU.INFORM);

		// ScopedPDU pdu = new ScopedPDU();
		// pdu.setType(PDU.INFORM);
		// sysUpTime
		long sysUpTime = (System.nanoTime() - startTime) / 10000000; // 10^-7
		pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(sysUpTime)));
		pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, SnmpConstants.linkDown));
		// payload
		// pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.1"+downIndex),
		// new Integer32(downIndex)));

		dataobject.setPdu(pdu);

		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));
		target.setAddress(GenericAddress.parse("udp:localhost/7162"));
		target.setRetries(0);
		target.setTimeout(2000);
		target.setVersion(SnmpConstants.version2c);
		dataobject.setCommunityTarget(target);

		KonnectorController.INSTANCE.getKonnectorByName(CLIENT_NAME).handle(dataobject);

		assertNotNull(dataobject.getResponsePdu());
		assertEquals(0, dataobject.getResponsePdu().getErrorStatus());
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
