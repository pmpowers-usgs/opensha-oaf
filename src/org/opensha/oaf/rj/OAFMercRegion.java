package org.opensha.oaf.rj;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import org.opensha.oaf.rj.OAFTectonicRegime;

// OAFMercRegion is a region for defining a tectonic regime.
// It includes a Region which defines a polygonal area on a Mercator
// projection (actually equirectangular projection) of the earth's surface,
// and minimum and maximum depths which define a depth range.
// It also includes the tectonic regime.

public class OAFMercRegion extends OAFRegion {

	// region - The region.

	private Region region;

	// min_depth - The minimum depth, in km (depth is positive down).

	private double min_depth;

	// max_depth - The maximum depth, in km (depth is positive down).

	private double max_depth;

	// toString - Convert to string.

	@Override
	public String toString() {
		String str = "OAFMercRegion\n"
			+ "\tRegime: " + get_regime() + "\n"
			+ "\tMinLat: " + region.getMinLat() + "\n"
			+ "\tMinLon: " + region.getMinLon() + "\n"
			+ "\tMaxLat: " + region.getMaxLat() + "\n"
			+ "\tMaxLon: " + region.getMaxLon() + "\n"
			+ "\tMinDepth: " + min_depth + "\n"
			+ "\tMaxDepth: " + max_depth;
		return str;
	}

	// Constructor saves the regime, region, and depth range.

	public OAFMercRegion (OAFTectonicRegime the_regime, Region the_region, double the_min_depth, double the_max_depth) {
		super (the_regime);
		region = the_region;
		min_depth = the_min_depth;
		max_depth = the_max_depth;
	}

	// contains - Determine whether the given location is inside the region.
	// See notes for Region.contains.

	@Override
	public boolean contains (Location loc) {
		return loc.getDepth() >= min_depth
			&& loc.getDepth() <= max_depth
			&& region.contains(loc);
	}

}
