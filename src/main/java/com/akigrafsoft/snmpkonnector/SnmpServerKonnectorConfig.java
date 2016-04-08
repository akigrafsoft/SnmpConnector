/**
 * Open-source, by AkiGrafSoft.
 *
 * $Id:  $
 *
 **/
package com.akigrafsoft.snmpkonnector;

import org.snmp4j.smi.GenericAddress;

import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.konnector.KonnectorConfiguration;

public class SnmpServerKonnectorConfig extends KonnectorConfiguration {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3344619604423353468L;

	private String genericAddress = "udp:127.0.0.1/161";

	public String getGenericAddress() {
		return genericAddress;
	}

	public void setGenericAddress(String genericAddress) {
		this.genericAddress = genericAddress;
	}

	// ------------------------------------------------------------------------
	// Configuration

	@Override
	public void audit() throws ExceptionAuditFailed {
		super.audit();

		if ((genericAddress == null) || genericAddress.equals("")) {
			throw new ExceptionAuditFailed("genericAddress must be provided and non empty");
		}
		try {
			if (GenericAddress.parse(genericAddress) == null) {
				throw new ExceptionAuditFailed(
						"genericAddress could not be parsed properly, format sample udp:127.0.0.1/161");
			}
		} catch (IllegalArgumentException e) {
			throw new ExceptionAuditFailed("genericAddress : IllegalArgumentException:" + e.getMessage());
		}
	}

}
