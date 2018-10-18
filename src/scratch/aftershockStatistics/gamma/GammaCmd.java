package scratch.aftershockStatistics.gamma;

import java.util.List;
import java.util.Scanner;

import java.io.Closeable;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import scratch.aftershockStatistics.AftershockStatsCalc;
import scratch.aftershockStatistics.AftershockStatsShadow;
import scratch.aftershockStatistics.AftershockVerbose;
import scratch.aftershockStatistics.ComcatAccessor;
import scratch.aftershockStatistics.CompactEqkRupList;
import scratch.aftershockStatistics.RJ_AftershockModel;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.commons.geo.Location;

import scratch.aftershockStatistics.util.MarshalReader;
import scratch.aftershockStatistics.util.MarshalWriter;
import scratch.aftershockStatistics.util.SimpleUtils;
import scratch.aftershockStatistics.util.ConsoleRedirector;
import scratch.aftershockStatistics.util.SphRegionWorld;
import scratch.aftershockStatistics.util.SphLatLon;
import scratch.aftershockStatistics.util.SphRegion;
import scratch.aftershockStatistics.util.SphRegionCircle;

import scratch.aftershockStatistics.aafs.ActionConfig;
import scratch.aftershockStatistics.aafs.ServerComponent;
import scratch.aftershockStatistics.aafs.ForecastMainshock;
import scratch.aftershockStatistics.aafs.ForecastParameters;
import scratch.aftershockStatistics.aafs.ForecastResults;



/**
 * Gamma statistical test command-line interface.
 * Author: Michael Barall 10/03/2018.
 */
public class GammaCmd {




	// cmd_list_events - List the events to use for the gamma test.
	// Command format:
	//  list_events  log_filename  event_list_filename  start_time  end_time  min_mag  max_mag
	// Get a world-wide catalog, and write a list of event ids to the given output file.
	// Events that are shadowed are excluded.
	// Times are ISO-8601 format, for example 2011-12-03T10:15:30Z.
	//
	// Usage requirements:
	// Set up ServerConfig.json to read from the desired catalog.

	public static void cmd_list_events(String[] args) {

		// 6 additional arguments

		if (args.length != 7) {
			System.err.println ("GammaCmd : Invalid 'list_events' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String event_list_filename = args[2];
				long startTime = SimpleUtils.string_to_time (args[3]);
				long endTime = SimpleUtils.string_to_time (args[4]);
				double min_mag = Double.parseDouble (args[5]);
				double max_mag = Double.parseDouble (args[6]);

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Event list filename: " + event_list_filename);
				System.out.println ("Start time: " + SimpleUtils.time_to_string(startTime));
				System.out.println ("End time: " + SimpleUtils.time_to_string(endTime));
				System.out.println ("Minimum magnitude: " + min_mag);
				System.out.println ("Maximum magnitude: " + max_mag);
				System.out.println ("");

				// Get configuration

				ActionConfig action_config = new ActionConfig();

				// Create the accessor

				ComcatAccessor accessor = new ComcatAccessor();

				// Construct the Region

				SphRegionWorld region = new SphRegionWorld ();

				// Call Comcat

				String null_event_id = null;
				double minDepth = ComcatAccessor.DEFAULT_MIN_DEPTH;
				double maxDepth = ComcatAccessor.DEFAULT_MAX_DEPTH;
				boolean wrapLon = false;
				boolean extendedInfo = false;

				ObsEqkRupList rup_list = accessor.fetchEventList (null_event_id, startTime, endTime,
						minDepth, maxDepth, region, wrapLon, extendedInfo,
						min_mag);

				// Display the information

				System.out.println ("Events returned by fetchEventList = " + rup_list.size());

				// Open the output file

				int events_accepted = 0;
				int events_shadowed = 0;
				int events_tested = 0;

				try (
					Writer writer = new BufferedWriter (new FileWriter (event_list_filename));
				){
					// Loop over ruptures

					for (ObsEqkRupture rup : rup_list) {

						// Silently discard events exceeding maximum magnitude

						if (rup.getMag() >= max_mag) {
							continue;
						}

						++events_tested;

						// Get the event id and time

						String rup_event_id = rup.getEventId();
						long rup_time = rup.getOriginTime();

						System.out.println ("Checking event: " + rup_event_id + " at " + SimpleUtils.time_to_string (rup_time));

						// Get find shadow parameters

						long max_forecast_lag = action_config.get_def_max_forecast_lag();
						long max_window_end_off = action_config.get_max_adv_window_end_off();

						long time_now = rup_time + max_forecast_lag + max_window_end_off;
						double search_radius = action_config.get_shadow_search_radius();
						long search_time_lo = rup_time - action_config.get_shadow_lookback_time();
						long search_time_hi = rup_time + max_forecast_lag + max_window_end_off;
						long centroid_rel_time_lo = 0L;
						long centroid_rel_time_hi = ServerComponent.DURATION_HUGE;
						double centroid_mag_floor = action_config.get_shadow_centroid_mag();
						double large_mag = action_config.get_shadow_large_mag();
						double[] separation = new double[2];

						// Run find_shadow

						ObsEqkRupture shadow;

						shadow = AftershockStatsShadow.find_shadow (rup, time_now,
							search_radius, search_time_lo, search_time_hi,
							centroid_rel_time_lo, centroid_rel_time_hi,
							centroid_mag_floor, large_mag, separation);

						// If we are shadowed ...

						if (shadow != null) {

							// Count it

							++events_shadowed;
						}

						// Otherwise, we are not shadowed ...

						else {

							// Count it

							++events_accepted;

							// Write to file
						
							writer.write (rup_event_id + "\n");
						}
					}
				}

				// Display the result

				System.out.println ("");
				System.out.println ("Events retrieved = " + rup_list.size());
				System.out.println ("Events tested = " + events_tested);
				System.out.println ("Events shadowed = " + events_shadowed);
				System.out.println ("Events accepted = " + events_accepted);

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_list_events had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_list_events had an exception");
            e.printStackTrace();
		}

		return;
	}




	// cmd_gamma_table - Write the gamma table for a list of earthquakes.
	// Command format:
	//  gamma_table  log_filename  event_list_filename  gamma_table_filename
	// Read the list of events, and for each event compute the log-likelihoods and event counts.
	// Sum over all events, and write the combined tables.
	//
	// Usage requirements:
	// Set up ServerConfig.json to read from the desired catalog.
	// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
	// Typical ActionConfig.json setup is:
	//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
	//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
	//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
	//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

	public static void cmd_gamma_table(String[] args) {

		// 3 additional arguments

		if (args.length != 4) {
			System.err.println ("GammaCmd : Invalid 'gamma_table' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String event_list_filename = args[2];
				String gamma_table_filename = args[3];

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Event list filename: " + event_list_filename);
				System.out.println ("Gamma table filename: " + gamma_table_filename);
				System.out.println ("");

				// Adjust verbosity

				ComcatAccessor.load_local_catalog();	// So catalog in use is displayed
				AftershockVerbose.set_verbose_mode (false);
				System.out.println ("");

				// Get configuration

				GammaConfig gamma_config = new GammaConfig();

				System.out.println (gamma_config.toString());
				System.out.println ("");

				// Total earthquake forecast set

				EqkForecastSet total = new EqkForecastSet();
				total.zero_init (gamma_config, gamma_config.eqk_summation_count);

				// Open the input file

				int events_processed = 0;

				try (
					Scanner scanner = new Scanner (new BufferedReader (new FileReader (event_list_filename)));
				){
					// Loop over earthquakes

					while (scanner.hasNext()) {

						// Count it

						++events_processed;

						// Read the event id

						String the_event_id = scanner.next();
						System.out.println ("Processing event " + events_processed + ": " + the_event_id);

						// Fetch the mainshock info

						ForecastMainshock fcmain = new ForecastMainshock();
						fcmain.setup_mainshock_only (the_event_id);

						// Compute models

						EqkForecastSet eqk_forecast_set = new EqkForecastSet();
						eqk_forecast_set.run_simulations (gamma_config,
							gamma_config.simulation_count, fcmain, false);

						// Add in to total

						total.add_from (gamma_config, eqk_forecast_set, gamma_config.eqk_summation_randomize);
					}
				}

				// Open the output file

				try (
					Writer writer = new BufferedWriter (new FileWriter (gamma_table_filename));
				){
					// Compute the gamma table and statistics table

					String gamma_table = total.single_event_gamma_to_string (gamma_config);
					String stats_table = total.compute_count_stats_to_string (gamma_config);

					// Write to file

					writer.write (gamma_table);
					writer.write ("\n");
					writer.write (stats_table);
				}

				// Display the result

				System.out.println ("");
				System.out.println ("Events processed = " + events_processed);

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_gamma_table had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_gamma_table had an exception");
            e.printStackTrace();
		}

		return;
	}




	// cmd_zepi_gamma_table - Write the gamma table for a list of earthquakes,
	// using zero epistemic uncertainty when running simulations.
	// Command format:
	//  gamma_table  log_filename  event_list_filename  gamma_table_filename
	// Read the list of events, and for each event compute the log-likelihoods and event counts.
	// Sum over all events, and write the combined tables.
	// Simulations are run with zero epistemic uncertainty.
	//
	// Usage requirements:
	// Set up ServerConfig.json to read from the desired catalog.
	// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
	// Typical ActionConfig.json setup is:
	//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
	//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
	//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
	//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

	public static void cmd_zepi_gamma_table(String[] args) {

		// 3 additional arguments

		if (args.length != 4) {
			System.err.println ("GammaCmd : Invalid 'zepi_gamma_table' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String event_list_filename = args[2];
				String gamma_table_filename = args[3];

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Event list filename: " + event_list_filename);
				System.out.println ("Gamma table filename: " + gamma_table_filename);
				System.out.println ("");

				// Adjust verbosity

				ComcatAccessor.load_local_catalog();	// So catalog in use is displayed
				AftershockVerbose.set_verbose_mode (false);
				System.out.println ("");

				// Get configuration

				GammaConfig gamma_config = new GammaConfig();

				System.out.println (gamma_config.toString());
				System.out.println ("");

				// Establish zero epistemic uncertainty

				gamma_config.no_epistemic_uncertainty = true;

				// Total earthquake forecast set

				EqkForecastSet total = new EqkForecastSet();
				total.zero_init (gamma_config, gamma_config.eqk_summation_count);

				// Open the input file

				int events_processed = 0;

				try (
					Scanner scanner = new Scanner (new BufferedReader (new FileReader (event_list_filename)));
				){
					// Loop over earthquakes

					while (scanner.hasNext()) {

						// Count it

						++events_processed;

						// Read the event id

						String the_event_id = scanner.next();
						System.out.println ("Processing event " + events_processed + ": " + the_event_id);

						// Fetch the mainshock info

						ForecastMainshock fcmain = new ForecastMainshock();
						fcmain.setup_mainshock_only (the_event_id);

						// Compute models

						EqkForecastSet eqk_forecast_set = new EqkForecastSet();
						eqk_forecast_set.run_simulations (gamma_config,
							gamma_config.simulation_count, fcmain, false);

						// Add in to total

						total.add_from (gamma_config, eqk_forecast_set, gamma_config.eqk_summation_randomize);
					}
				}

				// Open the output file

				try (
					Writer writer = new BufferedWriter (new FileWriter (gamma_table_filename));
				){
					// Compute the gamma table and statistics table

					String gamma_table = total.single_event_gamma_to_string (gamma_config);
					String stats_table = total.compute_count_stats_to_string (gamma_config);

					// Write to file

					writer.write (gamma_table);
					writer.write ("\n");
					writer.write (stats_table);
				}

				// Display the result

				System.out.println ("");
				System.out.println ("Events processed = " + events_processed);

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_zepi_gamma_table had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_zepi_gamma_table had an exception");
            e.printStackTrace();
		}

		return;
	}




	// cmd_stacked_gui_cat - Write a stacked aftershock catalog in GUI format.
	// Command format:
	//  stacked_gui_cat  log_filename  event_list_filename  gui_cat_filename  the_end_lag  main_mag
	// Read the list of events, create a stacked aftershock catalog, and write it in GUI catalog format.
	// The aftershock sequence extends from 0 to the_end_lag, which is given in java.time.Duration format.
	// The fictitious mainshock is at time 0 (Jan 1, 1970) with the given magnitude.

	public static void cmd_stacked_gui_cat(String[] args) {

		// 5 additional arguments

		if (args.length != 6) {
			System.err.println ("GammaCmd : Invalid 'stacked_gui_cat' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String event_list_filename = args[2];
				String gui_cat_filename = args[3];
				long the_end_lag = SimpleUtils.string_to_duration (args[4]);
				double main_mag = Double.parseDouble (args[5]);

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Event list filename: " + event_list_filename);
				System.out.println ("GUI catalog filename: " + gui_cat_filename);
				System.out.println ("Aftershock end lag: " + SimpleUtils.duration_raw_and_string (the_end_lag));
				System.out.println ("Mainshock magnitude: " + main_mag);
				System.out.println ("");

				// Adjust verbosity

				ComcatAccessor.load_local_catalog();	// So catalog in use is displayed
				AftershockVerbose.set_verbose_mode (false);
				System.out.println ("");

				// Get configuration

				GammaConfig gamma_config = new GammaConfig();

				System.out.println (gamma_config.toString());
				System.out.println ("");

				// Get the stacked aftershock sequence

				long origin_time = 0L;

				List<ObsEqkRupture> stacked_as = GammaUtils.get_stacked_aftershocks (
					event_list_filename, the_end_lag, origin_time, true);

				// Construct a fictitious mainshock

				double main_lat = 0.0;
				double main_lon = 0.0;
				double main_depth = 0.0;
				Location hypoLoc = new Location(main_lat, main_lon, main_depth);
				String eventId = "mainshock";
		
				ObsEqkRupture mainshock = new ObsEqkRupture (eventId, origin_time, hypoLoc, main_mag);

				// Write it in GUI catalog format

				GammaUtils.writeGUICatalogText (gui_cat_filename, mainshock, stacked_as);

				// Display the result

				System.out.println ("");
				System.out.println ("Number of stacked aftershocks = " + stacked_as.size());

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_stacked_gui_cat had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_stacked_gui_cat had an exception");
            e.printStackTrace();
		}

		return;
	}




	// cmd_sim_gamma_table - Write the gamma table for a list of earthquakes, using simulated aftershock sequences.
	// Command format:
	//  sim_gamma_table  log_filename  event_list_filename  gamma_table_filename  cat_min_mag  f_epi  num_rep   f_discard  f_randsum
	// Read the list of events, and for each event compute the log-likelihoods and event counts.
	// Sum over all events, and write the combined tables.
	// Aftershock sequences are simulated using generic models, with minimum magnitude cat_min_mag.
	// If f_epi is true, then epistemic uncertaintly is used when sampling the generic model.
	// Each event is repeated num_rep times.
	// if f_discard is true, then discard simulations that have an aftershock larger than the mainshock. (default true)
	// If f_randsum is true, then randomize sum over earthquakes.  (default true)
	//
	// Usage requirements:
	// Set up ServerConfig.json to read from the desired catalog.
	// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
	// Typical ActionConfig.json setup is:
	//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
	//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
	//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
	//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

	public static void cmd_sim_gamma_table(String[] args) {

		// 8 additional arguments

		if (args.length != 9) {
			System.err.println ("GammaCmd : Invalid 'sim_gamma_table' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String event_list_filename = args[2];
				String gamma_table_filename = args[3];
				double cat_min_mag = Double.parseDouble (args[4]);
				boolean f_epi = Boolean.parseBoolean (args[5]);
				int num_rep = Integer.parseInt (args[6]);
				boolean f_discard = Boolean.parseBoolean (args[7]);
				boolean f_randsum = Boolean.parseBoolean (args[8]);

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Event list filename: " + event_list_filename);
				System.out.println ("Gamma table filename: " + gamma_table_filename);
				System.out.println ("Minimum magnitude for simulated catalog: " + cat_min_mag);
				System.out.println ("Use epistemic uncertainty for simulation parameters: " + f_epi);
				System.out.println ("Number of repetitions: " + num_rep);
				System.out.println ("Discard simulations with a large aftershock: " + f_discard);
				System.out.println ("Randomize simulation sum over earthquakes: " + f_randsum);
				System.out.println ("");

				// Adjust verbosity

				ComcatAccessor.load_local_catalog();	// So catalog in use is displayed
				AftershockVerbose.set_verbose_mode (false);
				System.out.println ("");

				// Get configuration

				GammaConfig gamma_config = new GammaConfig();

				gamma_config.no_epistemic_uncertainty = !f_epi;
				//gamma_config.sim_start_off = 0L;
				gamma_config.discard_sim_with_large_as = f_discard;

				if (!( f_randsum )) {
					gamma_config.eqk_summation_count = gamma_config.simulation_count;
					gamma_config.eqk_summation_randomize = false;
				}

				System.out.println (gamma_config.toString());
				System.out.println ("");

				// Total earthquake forecast set

				EqkForecastSet total = new EqkForecastSet();
				total.zero_init (gamma_config, gamma_config.eqk_summation_count);

				// Open the input file

				int events_processed = 0;

				try (
					Scanner scanner = new Scanner (new BufferedReader (new FileReader (event_list_filename)));
				){
					// Loop over earthquakes

					while (scanner.hasNext()) {

						// Count it

						++events_processed;

						// Read the event id

						String the_event_id = scanner.next();
						System.out.println ("Processing event " + events_processed + ": " + the_event_id);

						// Repeat desired number of times

						for (int i_rep = 0; i_rep < num_rep; ++i_rep) {

							if (i_rep > 0) {
								System.out.println ("Processing event " + events_processed + " repetition " + (i_rep + 1) + ": " + the_event_id);
							}

							// Fetch the mainshock info

							ForecastMainshock fcmain = new ForecastMainshock();
							fcmain.setup_mainshock_only (the_event_id);

							// Compute models

							EqkForecastSet eqk_forecast_set = new EqkForecastSet();
							eqk_forecast_set.run_sim_simulations (gamma_config,
								gamma_config.simulation_count, fcmain, cat_min_mag, f_epi, false, false);

							// Add in to total

							total.add_from (gamma_config, eqk_forecast_set, gamma_config.eqk_summation_randomize);
						}
					}
				}

				// Open the output file

				try (
					Writer writer = new BufferedWriter (new FileWriter (gamma_table_filename));
				){
					// Compute the gamma table and statistics table

					String gamma_table = total.single_event_gamma_to_string (gamma_config);
					String stats_table = total.compute_count_stats_to_string (gamma_config);

					// Write to file

					writer.write (gamma_table);
					writer.write ("\n");
					writer.write (stats_table);
				}

				// Display the result

				System.out.println ("");
				System.out.println ("Events processed = " + events_processed);

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_gamma_table had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_gamma_table had an exception");
            e.printStackTrace();
		}

		return;
	}




	// cmd_seq_gamma_table - Write the gamma table for a single earthquakes, using simulated aftershock sequences.
	// Command format:
	//  seq_gamma_table  log_filename   gamma_table_filename  time  mag  lat  lon  cat_min_mag  f_epi  num_rep   f_discard  f_randsum
	// Create a simulated mainshock with the given time, magnitude, latitude, and longitude.
	// Simulate the event num_rep times, sum over all events, and write the combined tables.
	// Aftershock sequences are simulated using generic models, with minimum magnitude cat_min_mag.
	// If f_epi is true, then epistemic uncertaintly is used when sampling the generic model.
	// Times are ISO-8601 format, for example 2011-12-03T10:15:30Z.
	// if f_discard is true, then discard simulations that have an aftershock larger than the mainshock. (default true)
	// If f_randsum is true, then randomize sum over earthquakes.  (default true)
	//
	// Usage requirements:
	// Set up ServerConfig.json to read from the desired catalog.
	// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
	// Typical ActionConfig.json setup is:
	//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
	//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
	//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
	//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

	public static void cmd_seq_gamma_table(String[] args) {

		// 11 additional arguments

		if (args.length != 12) {
			System.err.println ("GammaCmd : Invalid 'seq_gamma_table' subcommand");
			return;
		}

		String log_filename = args[1];

		// Redirect to the log file

		try (

			// Console redirection and log

			ConsoleRedirector con_red = ConsoleRedirector.make_redirector (
				new BufferedOutputStream (new FileOutputStream (log_filename)), true, true);

		){

			try {

				// Parse arguments

				String gamma_table_filename = args[2];
				long time = SimpleUtils.string_to_time (args[3]);
				double mag = Double.parseDouble (args[4]);
				double lat = Double.parseDouble (args[5]);
				double lon = Double.parseDouble (args[6]);
				double cat_min_mag = Double.parseDouble (args[7]);
				boolean f_epi = Boolean.parseBoolean (args[8]);
				int num_rep = Integer.parseInt (args[9]);
				boolean f_discard = Boolean.parseBoolean (args[10]);
				boolean f_randsum = Boolean.parseBoolean (args[11]);

				// Say hello

				System.out.println ("Command line:");
				System.out.println (String.join ("  ", args));
				System.out.println ("");

				System.out.println ("Gamma table filename: " + gamma_table_filename);
				System.out.println ("Time: " + SimpleUtils.time_raw_and_string(time));
				System.out.println ("Magnitude: " + mag);
				System.out.println ("Latitude: " + lat);
				System.out.println ("Longitude: " + lon);
				System.out.println ("Minimum magnitude for simulated catalog: " + cat_min_mag);
				System.out.println ("Use epistemic uncertainty for simulation parameters: " + f_epi);
				System.out.println ("Number of repetitions: " + num_rep);
				System.out.println ("Discard simulations with a large aftershock: " + f_discard);
				System.out.println ("Randomize simulation sum over earthquakes: " + f_randsum);
				System.out.println ("");

				// Adjust verbosity

				ComcatAccessor.load_local_catalog();	// So catalog in use is displayed
				AftershockVerbose.set_verbose_mode (false);
				System.out.println ("");

				// Get configuration

				GammaConfig gamma_config = new GammaConfig();

				gamma_config.no_epistemic_uncertainty = !f_epi;
				gamma_config.sim_start_off = 0L;
				gamma_config.discard_sim_with_large_as = f_discard;

				if (!( f_randsum )) {
					gamma_config.eqk_summation_count = gamma_config.simulation_count;
					gamma_config.eqk_summation_randomize = false;
				}

				System.out.println (gamma_config.toString());
				System.out.println ("");

				// Total earthquake forecast set

				EqkForecastSet total = new EqkForecastSet();
				total.zero_init (gamma_config, gamma_config.eqk_summation_count);

				// Loop over repetitions

				int events_processed = 0;

				for (int i_rep = 0; i_rep < num_rep; ++i_rep) {

					// Count it

					++events_processed;

					// Read the event id

					System.out.println ("Processing event " + events_processed);

					// Create the mainshock info

					String network = "simnet";
					String code = "code" + events_processed;
					double depth = 0.0;

					ForecastMainshock fcmain = new ForecastMainshock();
					fcmain.setup_sim_mainshock (network, code, time, mag, lat, lon, depth);

					// Compute models

					EqkForecastSet eqk_forecast_set = new EqkForecastSet();
					eqk_forecast_set.run_sim_simulations (gamma_config,
						gamma_config.simulation_count, fcmain, cat_min_mag, f_epi, false, false);

					// Add in to total

					total.add_from (gamma_config, eqk_forecast_set, gamma_config.eqk_summation_randomize);
				}

				// Open the output file

				try (
					Writer writer = new BufferedWriter (new FileWriter (gamma_table_filename));
				){
					// Compute the gamma table and statistics table

					String gamma_table = total.single_event_gamma_to_string (gamma_config);
					String stats_table = total.compute_count_stats_to_string (gamma_config);

					// Write to file

					writer.write (gamma_table);
					writer.write ("\n");
					writer.write (stats_table);
				}

				// Display the result

				System.out.println ("");
				System.out.println ("Events processed = " + events_processed);

			}

			// Report any uncaught exceptions

			catch (Exception e) {
				System.out.println ("cmd_gamma_table had an exception");
				e.printStackTrace();
			}
		}

		// Report any uncaught exceptions

		catch (Exception e) {
			System.out.println ("cmd_gamma_table had an exception");
            e.printStackTrace();
		}

		return;
	}




	// Entry point.
	
	public static void main(String[] args) {

		// There needs to be at least one argument, which is the subcommand

		if (args.length < 1) {
			System.err.println ("GammaCmd : Missing subcommand");
			return;
		}

		switch (args[0].toLowerCase()) {


		// Subcommand : cmd_list_events
		// Command format:
		//  list_events  log_filename  event_list_filename  start_time  end_time  min_mag  max_mag
		// Get a world-wide catalog, and write a list of event ids to the given output file.
		// Events that are shadowed are excluded.
		// Times are ISO-8601 format, for example 2011-12-03T10:15:30Z.

		case "list_events":
			try {
				cmd_list_events(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		// Subcommand : cmd_gamma_table
		// Command format:
		//  gamma_table  log_filename  event_list_filename  gamma_table_filename
		// Read the list of events, and for each event compute the log-likelihoods and event counts.
		// Sum over all events, and write the combined tables.
		//
		// Usage requirements:
		// Set up ServerConfig.json to read from the desired catalog.
		// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
		// Typical ActionConfig.json setup is:
		//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
		//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
		//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
		//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

		case "gamma_table":
			try {
				cmd_gamma_table(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		// Subcommand : cmd_zepi_gamma_table
		// Command format:
		//  gamma_table  log_filename  event_list_filename  gamma_table_filename
		// Read the list of events, and for each event compute the log-likelihoods and event counts.
		// Sum over all events, and write the combined tables.
		// Simulations are run with zero epistemic uncertainty.
		//
		// Usage requirements:
		// Set up ServerConfig.json to read from the desired catalog.
		// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
		// Typical ActionConfig.json setup is:
		//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
		//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
		//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
		//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

		case "zepi_gamma_table":
			try {
				cmd_zepi_gamma_table(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		// Subcommand : cmd_stacked_gui_cat
		// Command format:
		//  stacked_gui_cat  log_filename  event_list_filename  gui_cat_filename  the_end_lag  main_mag
		// Read the list of events, create a stacked aftershock catalog, and write it in GUI catalog format.
		// The aftershock sequence extends from 0 to the_end_lag, which is given in java.time.Duration format.
		// The fictitious mainshock is at time 0 (Jan 1, 1970) with the given magnitude.

		case "stacked_gui_cat":
			try {
				cmd_stacked_gui_cat(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		// Subcommand : cmd_sim_gamma_table
		// Command format:
		//  sim_gamma_table  log_filename  event_list_filename  gamma_table_filename  cat_min_mag  f_epi
		// Read the list of events, and for each event compute the log-likelihoods and event counts.
		// Sum over all events, and write the combined tables.
		// Aftershock sequences are simulated using generic models, with minimum magnitude cat_min_mag.
		// If f_epi is true, then epistemic uncertaintly is used when sampling the generic model.
		//
		// Usage requirements:
		// Set up ServerConfig.json to read from the desired catalog.
		// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
		// Typical ActionConfig.json setup is:
		//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
		//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
		//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
		//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

		case "sim_gamma_table":
			try {
				cmd_sim_gamma_table(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		// Subcommand : cmd_seq_gamma_table
		// Command format:
		//  seq_gamma_table  log_filename   gamma_table_filename  time  mag  lat  lon  cat_min_mag  f_epi  num_rep
		// Create a simulated mainshock with the given time, magnitude, latitude, and longitude.
		// Simulate the event n_rep times, sum over all events, and write the combined tables.
		// Aftershock sequences are simulated using generic models, with minimum magnitude cat_min_mag.
		// If f_epi is true, then epistemic uncertaintly is used when sampling the generic model.
		// Times are ISO-8601 format, for example 2011-12-03T10:15:30Z.
		//
		// Usage requirements:
		// Set up ServerConfig.json to read from the desired catalog.
		// Set up ActionConfig.json to contain the desired forecast advisory windows and magnitude bins.
		// Typical ActionConfig.json setup is:
		//  "adv_min_mag_bins": [ 5.00, 6.00, 7.00 ],
		//  "adv_window_start_offs": [ "P0D", "P0D", "P0D", "P0D", "-P365D" ],
		//  "adv_window_end_offs": [ "P1D", "P7D", "P30D", "P365D", "P0D" ],
		//  "adv_window_names": [ "1 Day", "1 Week", "1 Month", "1 Year", "Retro" ],

		case "seq_gamma_table":
			try {
				cmd_seq_gamma_table(args);
            } catch (Exception e) {
                e.printStackTrace();
			}
			return;


		}

		// Unrecognized subcommand.

		System.err.println ("GammaCmd : Unrecognized subcommand : " + args[0]);
		return;
	}
}