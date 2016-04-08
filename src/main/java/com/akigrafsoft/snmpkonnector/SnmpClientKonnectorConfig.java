/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.konnector.SessionBasedClientKonnectorConfiguration;

/**
 * 
 * @author kmoyse
 *
 */
public class SnmpClientKonnectorConfig extends SessionBasedClientKonnectorConfiguration {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2269351914273576897L;

	private Protocol protocol = Protocol.UDP;

	public Protocol getProtocol() {
		return protocol;
	}

	public void setProtocol(Protocol protocol) {
		this.protocol = protocol;
	}

	// ------------------------------------------------------------------------
	// Configuration

	@Override
	public void audit() throws ExceptionAuditFailed {
		super.audit();
	}

}
