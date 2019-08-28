package org.opensha.oaf.aafs;

import org.opensha.oaf.util.MarshalReader;
import org.opensha.oaf.util.MarshalWriter;
import org.opensha.oaf.util.MarshalException;
import org.opensha.oaf.util.SimpleUtils;

import org.opensha.oaf.aafs.entity.PendingTask;
import org.opensha.oaf.aafs.entity.LogEntry;
import org.opensha.oaf.aafs.entity.CatalogSnapshot;
import org.opensha.oaf.aafs.entity.TimelineEntry;
import org.opensha.oaf.aafs.entity.AliasFamily;
import org.opensha.oaf.aafs.entity.RelayItem;


/**
 * Relay item payload for removal of forecasts from PDL.
 * Author: Michael Barall 06/30/2019.
 *
 * This item is generated when:
 * - The primary server calculates a forecast, and determines that there should not be a
 *   forecast displayed on PDL (it may also attempt to immediately delete any existing forecast).
 * - The secondary server switches to primary, and determines from a scan of the timeline
 *   that there should not be a forecast displayed on PDL.
 *
 * The relay_id should contain the authoritative event id.
 *
 * The riprem_remove_time should be the nominal time of the forecast (mainshock origin time plus
 * forecast lag).  This ensures that if the two servers disagree about whether there should
 * be a forecast, an item generated by one server does not cause deletion of the forecast
 * generated by the other server.
 *
 * The existence of this item means that any forecast sent to PDL before the riprem_remove_time of
 * this item can be immediately deleted from PDL.
 *
 * There is no need to retain items when riprem_remove_time is more than one year old,
 * because forecasts older than that can be deleted from PDL based on their age.
 * (Specifically, an item can be deleted if riprem_remove_time is older than
 * ActionConfig.get_removal_forecast_age() + ActionConfig.get_removal_update_skew().)
 */
public class RiPDLRemoval extends DBPayload {

	//----- Constants and variables -----

	// Reason code, which specifies why the forecast can be deleted from PDL.
	// Note: All reason codes are 3 digits, so that the JSON-encoded form
	// of this object has fixed length.

	public int riprem_reason;

	// Forecast stamp, which identifies the forecast.  Cannot be null.

	public ForecastStamp riprem_forecast_stamp;

	// The time when it was determined that the OAF product can be deleted.
	// Note: An offset is added when the object is serialized, so that the
	// JSON-encoded form of this object has fixed length.

	public long riprem_remove_time;

	// Reason codes, which are possible values of riprem_reason.

	public static final int RIPREM_REAS_MIN					= 101;
	public static final int RIPREM_REAS_SKIPPED_ANALYST		= 101;
		// A forecast was skipped at analyst request;
		// an existing OAF product may have been deleted.
	public static final int RIPREM_REAS_SKIPPED_INTAKE		= 102;
		// A forecast was skipped because the event does not pass the intake filter;
		// an existing OAF product may have been deleted.
	public static final int RIPREM_REAS_SKIPPED_SHADOWED	= 103;
		// A forecast was skipped because the mainshock was shadowed;
		// an existing OAF product may have been deleted.
	public static final int RIPREM_REAS_SKIPPED_FORESHOCK	= 104;
		// A forecast was skipped because the mainshock has an aftershock of larger magnitude;
		// an existing OAF product may have been deleted.
	public static final int RIPREM_REAS_MAX					= 104;
	
	// Return a string describing the riprem_reason.

	public String get_riprem_reason_as_string () {
		switch (riprem_reason) {
		case RIPREM_REAS_SKIPPED_ANALYST: return "RIPREM_REAS_SKIPPED_ANALYST";
		case RIPREM_REAS_SKIPPED_INTAKE: return "RIPREM_REAS_SKIPPED_INTAKE";
		case RIPREM_REAS_SKIPPED_SHADOWED: return "RIPREM_REAS_SKIPPED_SHADOWED";
		case RIPREM_REAS_SKIPPED_FORESHOCK: return "RIPREM_REAS_SKIPPED_FORESHOCK";
		}
		return "RIPREM_REAS_INVALID(" + riprem_reason + ")";
	}

	// Return a friendly string representation of riprem_forecast_stamp.

	public String get_riprem_forecast_stamp_as_string () {
		return riprem_forecast_stamp.get_friendly_string();
	}

	// Return a friendly string representation of ripdl_update_time.

	public String get_riprem_remove_time_as_string () {
		return SimpleUtils.time_raw_and_string (riprem_remove_time);
	}

	// Return true if this item is expired.
	// Parameters:
	//  time_now = Current time, in milliseconds since the epoch.
	//  action_config = Action configuration parameters.
	// Returns true if this item is old enough that it can be deleted.

	public boolean is_expired (long time_now, ActionConfig action_config) {
		if (riprem_remove_time + action_config.get_removal_forecast_age()
			+ action_config.get_removal_update_skew() < time_now) {

			return true;
		}
		return false;
	}

	// Serialization offset for longs.

	private static final long OFFSERL = 2000000000000000L;		// 2*10^15 ~ 60,000 years




	//----- Construction -----

	/**
	 * Default constructor does nothing.
	 */
	public RiPDLRemoval () {}


	// Set up the contents.

	public void setup (int the_riprem_reason, ForecastStamp the_riprem_forecast_stamp, long the_riprem_remove_time) {
		riprem_reason = the_riprem_reason;
		riprem_forecast_stamp = the_riprem_forecast_stamp;
		riprem_remove_time = the_riprem_remove_time;
		return;
	}




	//----- Marshaling -----

	// Marshal version number.

	private static final int MARSHAL_VER_1 = 60001;
	private static final int MARSHAL_VER_2 = 60002;

	private static final String M_VERSION_NAME = "RiPDLRemoval";

	// Marshal object, internal.

	@Override
	protected void do_marshal (MarshalWriter writer) {

		// Version

		int ver = MARSHAL_VER_2;

		writer.marshalInt (M_VERSION_NAME, ver);

		// Superclass

		super.do_marshal (writer);

		// Contents

		switch (ver) {

		case MARSHAL_VER_1: {

			long riprem_forecast_lag = riprem_forecast_stamp.get_forecast_lag();

			writer.marshalInt  ("riprem_reason"      , riprem_reason      );
			writer.marshalLong ("riprem_forecast_lag", riprem_forecast_lag + OFFSERL);
			writer.marshalLong ("riprem_remove_time" , riprem_remove_time  + OFFSERL);

			}
			break;

		case MARSHAL_VER_2:

			writer.marshalInt     (        "riprem_reason"        , riprem_reason        );
			ForecastStamp.marshal (writer, "riprem_forecast_stamp", riprem_forecast_stamp);
			writer.marshalLong    (        "riprem_remove_time"   , riprem_remove_time    + OFFSERL);

			break;
		}

		return;
	}

	// Unmarshal object, internal.

	@Override
	protected void do_umarshal (MarshalReader reader) {
	
		// Version

		int ver = reader.unmarshalInt (M_VERSION_NAME, MARSHAL_VER_1, MARSHAL_VER_2);

		// Superclass

		super.do_umarshal (reader);

		// Contents

		switch (ver) {

		case MARSHAL_VER_1: {

			long riprem_forecast_lag;

			riprem_reason       = reader.unmarshalInt  ("riprem_reason"      , RIPREM_REAS_MIN, RIPREM_REAS_MAX);
			riprem_forecast_lag = reader.unmarshalLong ("riprem_forecast_lag") - OFFSERL;
			riprem_remove_time  = reader.unmarshalLong ("riprem_remove_time" ) - OFFSERL;

			riprem_forecast_stamp = new ForecastStamp (riprem_forecast_lag);

			}
			break;

		case MARSHAL_VER_2:

			riprem_reason         = reader.unmarshalInt     (        "riprem_reason"        , RIPREM_REAS_MIN, RIPREM_REAS_MAX);
			riprem_forecast_stamp = ForecastStamp.unmarshal (reader, "riprem_forecast_stamp");
			riprem_remove_time    = reader.unmarshalLong    (        "riprem_remove_time"   ) - OFFSERL;

			break;
		}

		return;
	}

	// Marshal object.

	@Override
	public void marshal (MarshalWriter writer, String name) {
		writer.marshalMapBegin (name);
		do_marshal (writer);
		writer.marshalMapEnd ();
		return;
	}

	// Unmarshal object.

	@Override
	public RiPDLRemoval unmarshal (MarshalReader reader, String name) {
		reader.unmarshalMapBegin (name);
		do_umarshal (reader);
		reader.unmarshalMapEnd ();
		return this;
	}

	// Unmarshal object, for a relay item.

	@Override
	public RiPDLRemoval unmarshal_relay (RelayItem relit) {
		try {
			unmarshal (relit.get_details(), null);
		} catch (Exception e) {
			throw new DBCorruptException("Error unmarshaling relay item payload\n" + relit.toString() + "\nDump:\n" + relit.dump_details(), e);
		}
		return this;
	}

}
