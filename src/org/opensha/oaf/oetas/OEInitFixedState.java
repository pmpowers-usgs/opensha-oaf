package org.opensha.oaf.oetas;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.opensha.oaf.util.MarshalReader;
import org.opensha.oaf.util.MarshalWriter;
import org.opensha.oaf.util.MarshalException;


// Operational ETAS catalog initializer for a fixed initial state.
// Author: Michael Barall 01/27/2020.
//
// This object contains catalog parameters, info for the first (seed)
// generation, and a list of ruptures.  Each catalog is initialized with
// these same parameters and ruptures.

public class OEInitFixedState implements OEEnsembleInitializer {

	//----- State data -----

	// Parameters for the catalog.

	private OECatalogParams cat_params;

	// Information for the first (seed) generation.

	private OEGenerationInfo seed_gen_info;

	// Ruptures for the first (seed) generation.

	private OERupture[] ruptures;




	//----- Construction -----




	// Erase the contents.

	public void clear () {
		cat_params = null;
		seed_gen_info = null;
		ruptures = null;
		return;
	}




	// Default constructor.

	public OEInitFixedState () {
		clear();
	}




	// Set up to begin seeding.
	// Parameters:
	//  the_cat_params = The catalog parameters.
	//  the_seed_gen_info = Information for the first (seed) generation.
	//  the_ruptures = List of ruptures for the first generation
	// Note: The function retains the passed-in structures.

	public void setup (OECatalogParams the_cat_params, OEGenerationInfo the_seed_gen_info, List<OERupture> the_ruptures) {

		// Parameter validation

		if (!( the_ruptures.size() >= 1 )) {
			throw new IllegalArgumentException ("OEInitFixedState.setup: Empty list of ruptures");
		}

		// Copy parameters

		cat_params = the_cat_params;
		seed_gen_info = the_seed_gen_info;
		ruptures = the_ruptures.toArray (new OERupture[0]);

		return;
	}




	//----- Seeders -----




	// Seeder for fixed state.

	private class SeederFixed implements OECatalogSeeder {

		//----- Control -----

		// True if seeder is open.

		private boolean f_open;


		//----- Construction -----

		// Default constructor.

		public SeederFixed () {
			f_open = false;
		}

		//----- Open/Close methods (Implementation of OECatalogSeeder) -----

		// Open the catalog seeder.
		// Perform any setup needed.

		@Override
		public void open () {

			// Mark it open

			f_open = true;
			return;
		}

		// Close the catalog seeder.
		// Perform any final tasks needed.

		@Override
		public void close () {

			// If open ...

			if (f_open) {
			
				// Mark it closed

				f_open = false;
			}
			return;
		}


		//----- Data methods (Implementation of OECatalogSeeder) -----

		// Seed a catalog.
		// Parameters:
		//  comm = Communication area, with per-catalog values set up.
		// This function should perform the following steps:
		// 1. Construct a OECatalogParams object containing the catalog parameters.
		// 2. Call comm.cat_builder.begin_catalog() to begin constructing the catalog.
		// 3. Construct a OEGenerationInfo object containing info for the first (seed) generation.
		// 4. Call comm.cat_builder.begin_generation() to begin the first (seed) generation.
		// 5. Call comm.cat_builder.add_rup one or more times to add the seed ruptures.
		// 6. Call comm.cat_builder.end_generation() to end the first (seed) generation.

		@Override
		public void seed_catalog (OECatalogSeedComm comm) {

			// Begin the catalog

			comm.cat_builder.begin_catalog (cat_params);

			// Begin the first generation

			comm.cat_builder.begin_generation (seed_gen_info);

			// Insert the ruptures

			for (OERupture rup : ruptures) {
				comm.cat_builder.add_rup (rup);
			}

			// End the first generation

			comm.cat_builder.end_generation();
		
			return;
		}

	}




	//----- Implementation of OEEnsembleInitializer -----




	// Make a catalog seeder.
	// Returns a seeder which is able to seed the contents of one catalog
	// (or several catalogs in succession).
	// This function may be called repeatedly to create several seeders,
	// which can be used in multiple worker threads.
	// Threading: Can be called in multiple threads, before or after the call to
	// begin_initialization, and while there are existing open seeders, and so
	// must be properly synchronized.
	// Note: The returned seeder cannot be opened until after the call to
	// begin_initialization, and must be closed before the call to end_initialization.
	// Note: The returned seeder can be opened and closed repeatedly to seed
	// multiple catalogs.

	@Override
	public OECatalogSeeder make_seeder () {
		OECatalogSeeder seeder;
		seeder = new SeederFixed ();
		return seeder;
	}




	// Begin initializing catalogs.
	// This function should be called before any other control methods.
	// The initializer should allocate any resources it needs.
	// Threading: No other thread should be accessing this object,
	// and none of its seeders can be open.

	@Override
	public void begin_initialization () {
		return;
	}




	// End initializing catalogs.
	// This function should be called after all other control functions.
	// It provides an opportunity for the initializer to release any resources it holds.
	// Threading: No other thread should be accessing this object,
	// and none of its seeders can be open.

	@Override
	public void end_initialization () {
		return;
	}




	//----- Readout functions -----




	// Get the catalog parameters.

	public OECatalogParams get_cat_params () {
		return cat_params;
	}


	// Get the information for the first (seed) generation.

	public OEGenerationInfo get_seed_gen_info () {
		return seed_gen_info;
	}


	// Get the number of ruptures.

	public int get_rupture_count () {
		if (ruptures == null) {
			return 0;
		}
		return ruptures.length;
	}


	// Get the n-th rupture.

	public OERupture get_rupture (int n) {
		return ruptures[n];
	}




	//----- Marshaling -----

	// Marshal version number.

	private static final int MARSHAL_VER_1 = 80001;

	private static final String M_VERSION_NAME = "OEInitFixedState";

	// Marshal object, internal.

	private void do_marshal (MarshalWriter writer) {

		// Version

		int ver = MARSHAL_VER_1;

		writer.marshalInt (M_VERSION_NAME, ver);

		// Contents

		switch (ver) {

		case MARSHAL_VER_1: {

			OECatalogParams.static_marshal  (writer, "cat_params   ", cat_params   );
			OEGenerationInfo.static_marshal (writer, "seed_gen_info", seed_gen_info);
			OERupture.marshal_array         (writer, "ruptures"     , ruptures     );

		}
		break;

		}

		return;
	}

	// Unmarshal object, internal.

	private void do_umarshal (MarshalReader reader) {
	
		// Version

		int ver = reader.unmarshalInt (M_VERSION_NAME, MARSHAL_VER_1, MARSHAL_VER_1);

		// Contents

		switch (ver) {

		case MARSHAL_VER_1: {

			cat_params    = OECatalogParams.static_unmarshal  (reader, "cat_params"   );
			seed_gen_info = OEGenerationInfo.static_unmarshal (reader, "seed_gen_info");
			ruptures      = OERupture.unmarshal_array         (reader, "ruptures"     );

		}
		break;

		}

		return;
	}

	// Marshal object.

	public void marshal (MarshalWriter writer, String name) {
		writer.marshalMapBegin (name);
		do_marshal (writer);
		writer.marshalMapEnd ();
		return;
	}

	// Unmarshal object.

	public OEInitFixedState unmarshal (MarshalReader reader, String name) {
		reader.unmarshalMapBegin (name);
		do_umarshal (reader);
		reader.unmarshalMapEnd ();
		return this;
	}

	// Marshal object.

	public static void static_marshal (MarshalWriter writer, String name, OEInitFixedState accumulator) {
		writer.marshalMapBegin (name);
		accumulator.do_marshal (writer);
		writer.marshalMapEnd ();
		return;
	}

	// Unmarshal object.

	public static OEInitFixedState static_unmarshal (MarshalReader reader, String name) {
		OEInitFixedState accumulator = new OEInitFixedState();
		reader.unmarshalMapBegin (name);
		accumulator.do_umarshal (reader);
		reader.unmarshalMapEnd ();
		return accumulator;
	}




	//----- Testing -----




	public static void main(String[] args) {

		// There needs to be at least one argument, which is the subcommand

		if (args.length < 1) {
			System.err.println ("OEInitFixedState : Missing subcommand");
			return;
		}




		// Subcommand : Test #1
		// Command format:
		//  test1  test_gen_count  test_gen_size
		// Build a catalog with test_gen_count generations.
		// Each generation has a mean of test_gen_size ruptures, but varies randomly.
		// Ruptures are generated randomly.
		// Then, display the catalog summary and generation list.
		// Then, scan the catalog and compare to the data used to build it.
		// Note: Similar to test #2 in OECatalogStorage, except uses OEInitFixedState
		// to generate the first (seed) generation.

		if (args[0].equalsIgnoreCase ("test1")) {

			// 2 additional arguments

			if (args.length != 3) {
				System.err.println ("OEInitFixedState : Invalid 'test1' subcommand");
				return;
			}

			try {

				int test_gen_count = Integer.parseInt (args[1]);
				int test_gen_size = Integer.parseInt (args[2]);

				// Say hello

				System.out.println ("Generating catalog with random data, using initializer");
				System.out.println ("test_gen_count = " + test_gen_count);
				System.out.println ("test_gen_size = " + test_gen_size);

				// Get the random number generator

				OERandomGenerator rangen = OERandomGenerator.get_thread_rangen();

				// Allocate the storage

				OECatalogStorage cat_storage = new OECatalogStorage();

				// Input catalog parameters

				OECatalogParams in_cat_params = (new OECatalogParams()).set_to_random (rangen);
				System.out.println ();
				System.out.println (in_cat_params.toString());

				// The initializer to use

				OEInitFixedState initializer = new OEInitFixedState();

				// The seeder communication area

				OECatalogSeedComm seed_comm = new OECatalogSeedComm();

				// Begin the catalog

				//cat_storage.begin_catalog (in_cat_params);

				// Input parameters for size, generation size, generation info, and rupture

				int in_size = 0;
				int[] in_gen_size = new int[test_gen_count];
				OEGenerationInfo[] in_gen_info = new OEGenerationInfo[test_gen_count];
				OERupture[][] in_rup = new OERupture[test_gen_count][];

				// Loop over generations

				for (int i_gen = 0; i_gen < test_gen_count; ++i_gen) {
				
					// Select a size for this generation

					//in_gen_size[i_gen] = rangen.uniform_int_sample (test_gen_size/2 + 1, 3*test_gen_size/2 + 1);
					in_gen_size[i_gen] = Math.max (1, rangen.poisson_sample ((double)test_gen_size));

					in_size += in_gen_size[i_gen];
					in_rup[i_gen] = new OERupture[in_gen_size[i_gen]];

					// Input generation info

					in_gen_info[i_gen] = (new OEGenerationInfo()).set_to_random (rangen);

					if (i_gen < 5) {
						System.out.println ();
						System.out.println (in_gen_info[i_gen].one_line_string (i_gen, in_gen_size[i_gen]));
					}

					// Begin the generation, if not the first generation

					if (i_gen > 0) {
						cat_storage.begin_generation (in_gen_info[i_gen]);
					}

					// Loop over ruptures

					for (int j_rup = 0; j_rup < in_gen_size[i_gen]; ++j_rup) {
					
						// Input rupture

						in_rup[i_gen][j_rup] = (new OERupture()).set_to_random (rangen);

						if (i_gen < 5 && j_rup < 10) {
							System.out.println (in_rup[i_gen][j_rup].one_line_string (j_rup));
						}

						// Insert rupture into catalog, if not the first generation

						if (i_gen > 0) {
							cat_storage.add_rup (in_rup[i_gen][j_rup]);
						}
					}

					// End the generation, if not the first generation

					if (i_gen > 0) {
						cat_storage.end_generation();
					}

					// If the first generation, set up and use the initializer ...

					if (i_gen == 0) {

						// Transfer the ruptures into a list

						ArrayList<OERupture> the_ruptures = new ArrayList<OERupture>();
						for (OERupture rup : in_rup[i_gen]) {
							the_ruptures.add (rup);
						}

						// Set up the initializer

						initializer.setup (in_cat_params, in_gen_info[i_gen], the_ruptures);

						// Begin initialization

						initializer.begin_initialization();

						// Obtain a seeder

						OECatalogSeeder seeder = initializer.make_seeder();

						// Set up the seeder communication area

						seed_comm.setup_seed_comm (cat_storage, rangen);

						// Open the seeder

						seeder.open();

						// Seed the catalog

						seeder.seed_catalog (seed_comm);

						// Close the seeder

						seeder.close();

						// End initialization

						initializer.end_initialization();
					}
				}

				// End the catalog

				cat_storage.end_catalog();

				// Display catalog summary and generation list

				System.out.println ();
				System.out.println ("Catalog summary...");
				System.out.println ();
				System.out.println (cat_storage.summary_and_gen_list_string());

				// Begin catalog check

				System.out.println ();
				System.out.println ("Checking catalog...");

				// Structures for output and comparison

				OECatalogParams out_cat_params = new OECatalogParams();
				int out_size;
				int out_gen_count;
				int out_gen_size;
				OEGenerationInfo out_gen_info = new OEGenerationInfo();
				OERupture out_rup = new OERupture();
				OERupture cmp_rup = new OERupture();

				// Error count

				int err_count = 0;

				// Check catalog parameters

				cat_storage.get_cat_params (out_cat_params);
				if (!( out_cat_params.check_param_equal(in_cat_params) )) {
					System.out.println ("MISMATCH for catalog parameters");
					System.out.println ("Expected: " + in_cat_params.toString());
					System.out.println ("Got: " + out_cat_params.toString());
					++err_count;
				}

				// Check catalog total size

				out_size = cat_storage.size();
				if (!( out_size == in_size )) {
					System.out.println ("MISMATCH for catalog total size");
					System.out.println ("Expected: " + in_size);
					System.out.println ("Got: " + out_size);
					++err_count;
				}

				// Check catalog generation count

				out_gen_count = cat_storage.get_gen_count();
				if (!( out_gen_count == test_gen_count )) {
					System.out.println ("MISMATCH for catalog generation count");
					System.out.println ("Expected: " + test_gen_count);
					System.out.println ("Got: " + out_gen_count);
					++err_count;
				}

				// Loop over generations

				for (int i_gen = 0; i_gen < test_gen_count; ++i_gen) {

					// Check catalog generation size

					out_gen_size = cat_storage.get_gen_size (i_gen);
					if (!( out_gen_size == in_gen_size[i_gen] )) {
						System.out.println ("MISMATCH for generation " + i_gen + " size");
						System.out.println ("Expected: " + in_gen_size[i_gen]);
						System.out.println ("Got: " + out_gen_size);
						++err_count;
					}

					// Check catalog generation information

					cat_storage.get_gen_info (i_gen, out_gen_info);	
					if (!( out_gen_info.check_gen_equal(in_gen_info[i_gen]) )) {
						System.out.println ("MISMATCH for generation " + i_gen + " information");
						System.out.println ("Expected: " + in_gen_info[i_gen].one_line_string());
						System.out.println ("Got: " + out_gen_info.one_line_string());
						++err_count;
					}

					if (err_count >= 10) {
						System.out.println ("Early termination, error count = " + err_count);
						return;
					}

					// Loop over ruptures

					for (int j_rup = 0; j_rup < in_gen_size[i_gen]; ++j_rup) {

						// Check catalog rupture

						cat_storage.get_rup (i_gen, j_rup, out_rup);
						OECatalogStorage.test_trunc_rup_as_if_stored (in_rup[i_gen][j_rup], cmp_rup);
						if (!( out_rup.check_rup_equal(cmp_rup) )) {
							System.out.println ("MISMATCH for generation " + i_gen + " rupture " + j_rup);
							System.out.println ("Original: " + in_rup[i_gen][j_rup].one_line_string());
							System.out.println ("Expected: " + cmp_rup.one_line_string());
							System.out.println ("Got: " + out_rup.one_line_string());
							++err_count;
							if (err_count >= 10) {
								System.out.println ("Early termination, error count = " + err_count);
								return;
							}
						}
					}
				}

				// Final result

				System.out.println ();
				System.out.println ("Error count = " + err_count);

			} catch (Exception e) {
				e.printStackTrace();
			}

			return;
		}




		// Unrecognized subcommand.

		System.err.println ("OEInitFixedState : Unrecognized subcommand : " + args[0]);
		return;

	}

}
