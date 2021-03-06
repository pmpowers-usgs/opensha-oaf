package org.opensha.oaf.aafs;

import java.util.List;

import org.opensha.oaf.aafs.entity.PendingTask;
import org.opensha.oaf.aafs.entity.LogEntry;
import org.opensha.oaf.aafs.entity.CatalogSnapshot;
import org.opensha.oaf.aafs.entity.TimelineEntry;
import org.opensha.oaf.aafs.entity.AliasFamily;
import org.opensha.oaf.aafs.entity.RelayItem;

import org.opensha.oaf.util.MarshalReader;
import org.opensha.oaf.util.MarshalWriter;
import org.opensha.oaf.util.SimpleUtils;

import org.opensha.commons.data.comcat.ComcatException;
import org.opensha.oaf.rj.CompactEqkRupList;

import org.opensha.oaf.pdl.PDLCodeChooserOaf;
import org.opensha.oaf.pdl.PDLProductBuilderOaf;
import org.opensha.oaf.pdl.PDLSender;
import gov.usgs.earthquake.product.Product;

import org.json.simple.JSONObject;


/**
 * Support functions for PDL reporting.
 * Author: Michael Barall 06/24/2018.
 */
public class PDLSupport extends ServerComponent {




	//----- Task execution subroutines : PDL operations -----




//	// Send a report to PDL.
//	// Throw an exception if the report failed.
//
//	public void send_pdl_report (TimelineStatus tstatus) throws Exception {
//
//		// The JSON file to send
//
//		String jsonText = tstatus.forecast_results.get_pdl_model();
//
//		// The event network and code
//
//		String eventNetwork = tstatus.forecast_mainshock.mainshock_network;
//		String eventCode = tstatus.forecast_mainshock.mainshock_code;
//
//		// The event ID, which for us identifies the timeline
//
//		String eventID = sg.alias_sup.timeline_id_to_pdl_code (tstatus.event_id);
//
//		// Modification time, 0 means now
//
//		long modifiedTime = 0L;
//
//		// Review status, false means automatically generated
//
//		boolean isReviewed = false;
//
//		// Build the product
//
//		Product product = PDLProductBuilderOaf.createProduct (eventID, eventNetwork, eventCode, isReviewed, jsonText, modifiedTime);
//
//		// Sign the product
//
//		PDLSender.signProduct(product);
//
//		// Send the product, true means it is text
//
//		PDLSender.sendProduct(product, true);
//
//		return;
//	}




	// Send a report to PDL.
	// Return the code used to send to PDL, null if not stored due to conflict with existing forecast.
	// Throw an exception if the report failed.
	// If successful send with no conflict, then set tstatus.pdl_product_code to the product code used. 
	// Use this version if the catalog is in tstatus.forecast_results.

	public String send_pdl_report (TimelineStatus tstatus) throws Exception {

		// Collect the forecast data

		ForecastData forecast_data = new ForecastData();
		forecast_data.set_data (tstatus.entry_time, tstatus.forecast_mainshock, tstatus.forecast_params,
							tstatus.forecast_results, tstatus.analyst_options);

		// The event ID, which for us identifies the timeline

		String eventID = sg.alias_sup.timeline_id_to_pdl_code (tstatus.event_id);

		// The suggested product code, either derived from the event ID or saved from the prior Send

		String suggested_code = ((tstatus.has_pdl_product_code()) ? (tstatus.pdl_product_code) : eventID);

		// Review status, false means automatically generated

		boolean isReviewed = false;

		// Build the product

		Product product = forecast_data.make_pdl_product (suggested_code, isReviewed);

		// Stop if conflict

		if (product == null) {
			System.out.println ("ForecastData.make_pdl_product returned null, indicating conflict");
			return null;
		}

		// Sign the product

		PDLSender.signProduct(product);

		// Send the product, true means it is text

		PDLSender.sendProduct(product, true);

		// Save the product code that was used in the send

		tstatus.pdl_product_code = ((forecast_data.pdl_event_id.equals (eventID)) ? "" : (forecast_data.pdl_event_id));

		return forecast_data.pdl_event_id;
	}




	// Send a report to PDL.
	// Return the code used to send to PDL, null if not stored due to conflict with existing forecast.
	// Throw an exception if the report failed.
	// If successful send with no conflict, then set tstatus.pdl_product_code to the product code used. 
	// Use this version to supply the catalog separately.

	public String send_pdl_report (TimelineStatus tstatus, CompactEqkRupList catalog) throws Exception {

		// Collect the forecast data

		ForecastData forecast_data = new ForecastData();
		forecast_data.set_data (tstatus.entry_time, tstatus.forecast_mainshock, tstatus.forecast_params,
							tstatus.forecast_results, tstatus.analyst_options, catalog);

		// The event ID, which for us identifies the timeline

		String eventID = sg.alias_sup.timeline_id_to_pdl_code (tstatus.event_id);

		// The suggested product code, either derived from the event ID or saved from the prior Send

		String suggested_code = ((tstatus.has_pdl_product_code()) ? (tstatus.pdl_product_code) : eventID);

		// Review status, false means automatically generated

		boolean isReviewed = false;

		// Build the product

		Product product = forecast_data.make_pdl_product (suggested_code, isReviewed);

		// Stop if conflict

		if (product == null) {
			System.out.println ("ForecastData.make_pdl_product returned null, indicating conflict");
			return null;
		}

		// Sign the product

		PDLSender.signProduct(product);

		// Send the product, true means it is text

		PDLSender.sendProduct(product, true);

		// Save the product code that was used in the send

		tstatus.pdl_product_code = ((forecast_data.pdl_event_id.equals (eventID)) ? "" : (forecast_data.pdl_event_id));

		return forecast_data.pdl_event_id;
	}




	// Delete the OAF produts for an event.
	// Parameters:
	//  fcmain = Forecast mainshock structure, already filled in.
	//  riprem_reason = Relay item action code, from RiPDLRemoval.RIPREM_REAS_XXXXX.
	//  riprem_forecast_stamp = Forecast stamp for this action, contained forecast lag can be -1L if unknown.
	// If a forecast lag is supplied, then the removal time is equal to the mainshock time
	//  plus the forecast lag.  Otherwise, the removel time is the current time.
	// If this is a primary machine, then this function:
	// - Writes a relay item for the given action code.
	// - Deletes all OAF products associated with the event.
	// - Writes a log message.
	// If an exception occurs during the deletion of OAF products, this function
	//  absorbs the exception and does not propagate it to hogher levels.
	// If this is a secondary machine, the function does nothing.
	// Note: Exceptions do not trigger any special retry logic.  The relay item is submitted
	//  to the database before attempting the PDL deletion, so following an exception the
	//  cleanup process should eventually finish the deletion.

	public void delete_oaf_products (ForecastMainshock fcmain, int riprem_reason, ForecastStamp riprem_forecast_stamp) {

		// If this is not primary, then do nothing

		if (!( is_pdl_primary() )) {
			return;
		}

		// Write the relay item

		String event_id = fcmain.get_pdl_relay_id();
		long relay_time = sg.task_disp.get_time();
		boolean f_force = false;

		long riprem_remove_time;
		if (riprem_forecast_stamp.get_forecast_lag() < 0L) {
			riprem_remove_time = sg.task_disp.get_time();
		} else {
			riprem_remove_time = fcmain.mainshock_time + riprem_forecast_stamp.get_forecast_lag();
		}

		RelayItem relit = sg.relay_sup.submit_prem_relay_item (event_id, relay_time, f_force, riprem_reason, riprem_forecast_stamp, riprem_remove_time);
	
		// Delete the old OAF products

		long delres;

		try {
			JSONObject geojson = fcmain.mainshock_geojson;	// it's OK if this is null
			boolean f_gj_prod = true;
			String queryID = fcmain.mainshock_event_id;
			boolean isReviewed = false;
			long cutoff_time = 0L;

			long reviewed_time = 0L;
			if (riprem_reason == RiPDLRemoval.RIPREM_REAS_SKIPPED_ANALYST) {
				reviewed_time = 1L;		// for analyst, don't delete reviewed products
			}

			delres = PDLCodeChooserOaf.deleteOldOafProducts_v2 (null, geojson, f_gj_prod, queryID, isReviewed, cutoff_time, reviewed_time);

		} catch (Exception e) {

			// Run cleanup process after retry interval

			sg.cleanup_sup.set_cleanup_retry();

			// Just log the exception and done

			sg.log_sup.report_pdl_delete_exception (event_id, e);
			return;
		}

		// Log successful deletion, if we deleted something

		if (delres == PDLCodeChooserOaf.DOOP_DELETED) {
			sg.log_sup.report_pdl_delete_ok (event_id);
		}

		// Done

		return;
	}




	//----- Primary/Secondary -----




	// Force primary/secondary mode (used for testing):
	// 0 = normal operation, 1 = force primary mode, 2 = force secondary mode.

	private int force_primary;


	// Set the force primary mode.

	public void set_force_primary (int the_force_primary) {
		force_primary = the_force_primary;
		return;
	}




	// Return true if this machine is primary for sending reports to PDL, false if secondary

	public boolean is_pdl_primary () {

		// Check if mode is forced

		switch (force_primary) {
		case 1:
			return true;
		case 2:
			return false;
		}

		// Get the primary mode from the relay link

		return sg.relay_link.is_primary_state();

		//  // For now, just assume primary
		//  
		//  return true;
	}




	//----- Construction -----


	// Default constructor.

	public PDLSupport () {
	
		force_primary = 0;
	
	}


	// Set up this component by linking to the server group.
	// A subclass may override this to perform additional setup operations.

	@Override
	public void setup (ServerGroup the_sg) {
		super.setup (the_sg);

		force_primary = 0;

		return;
	}

}
