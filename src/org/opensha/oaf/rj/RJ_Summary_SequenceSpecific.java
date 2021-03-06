package org.opensha.oaf.rj;

import org.opensha.oaf.util.MarshalReader;
import org.opensha.oaf.util.MarshalWriter;
import org.opensha.oaf.util.MarshalException;

public class RJ_Summary_SequenceSpecific extends RJ_Summary {

	// Summary values, see RJ_AftershockModel_SequenceSpecific for description.

	private MagCompFn magCompFn           ;
	private double    magCat              ;
	private double    dataStartTimeDays   ;
	private double    dataEndTimeDays     ;
	private int       numAftershocks      ;

	
	/**
	 * Default constructor.
	 */
	public RJ_Summary_SequenceSpecific() {
		super();
	}

	
	/**
	 * Construct from R&J model.
	 */
	public RJ_Summary_SequenceSpecific(RJ_AftershockModel_SequenceSpecific model) {
		super (model);

		this.magCompFn            = model.magCompFn            ;
		this.magCat               = model.magCat               ;
		this.dataStartTimeDays    = model.dataStartTimeDays    ;
		this.dataEndTimeDays      = model.dataEndTimeDays      ;
		this.numAftershocks       = model.numAftershocks       ;
	}

	
	/**
	 * Getters.
	 */
	public MagCompFn get_magCompFn           () {return magCompFn           ;}
	public double    get_magCat              () {return magCat              ;}
	public double    get_dataStartTimeDays   () {return dataStartTimeDays   ;}
	public double    get_dataEndTimeDays     () {return dataEndTimeDays     ;}
	public int       get_numAftershocks      () {return numAftershocks      ;}



	@Override
	public String toString() {
		return super.toString() + "\n" +
			"magCompFn            = " + magCompFn.toString      () + "\n" +
			"magCat               = " + get_magCat              () + "\n" +
			"dataStartTimeDays    = " + get_dataStartTimeDays   () + "\n" +
			"dataEndTimeDays      = " + get_dataEndTimeDays     () + "\n" +
			"numAftershocks       = " + get_numAftershocks      () ;
	}




	//----- Marshaling -----

	// Marshal version number.

	private static final int MARSHAL_VER_1 = 7001;
	private static final int MARSHAL_VER_2 = 7002;

	private static final String M_VERSION_NAME = "RJ_Summary_SequenceSpecific";

	// Get the type code.

	@Override
	protected int get_marshal_type () {
		return MARSHAL_RJSEQ;
	}

	// Marshal object. internal.

	@Override
	protected void do_marshal (MarshalWriter writer) {

		// Version

		int ver = MARSHAL_VER_2;
		writer.marshalInt (M_VERSION_NAME, ver);

		// Superclass

		super.do_marshal (writer);

		// Contents

		switch (ver) {

		default:
			throw new MarshalException ("RJ_Summary_SequenceSpecific.do_marshal: Unknown version number: " + ver);

		case MARSHAL_VER_1: {

			writer.marshalDouble ("capG"                , magCompFn.getLegacyCapG());
			writer.marshalDouble ("capH"                , magCompFn.getLegacyCapH());
			writer.marshalDouble ("magCat"              , magCat              );
			writer.marshalDouble ("dataStartTimeDays"   , dataStartTimeDays   );
			writer.marshalDouble ("dataEndTimeDays"     , dataEndTimeDays     );
			writer.marshalInt    ("numAftershocks"      , numAftershocks      );

		}
		break;

		case MARSHAL_VER_2: {

			MagCompFn.marshal_poly (writer, "magCompFn" , magCompFn           );
			writer.marshalDouble ("magCat"              , magCat              );
			writer.marshalDouble ("dataStartTimeDays"   , dataStartTimeDays   );
			writer.marshalDouble ("dataEndTimeDays"     , dataEndTimeDays     );
			writer.marshalInt    ("numAftershocks"      , numAftershocks      );

		}
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

		default:
			throw new MarshalException ("RJ_Summary_SequenceSpecific.do_umarshal: Unknown version number: " + ver);

		case MARSHAL_VER_1: {

			double capG          = reader.unmarshalDouble ("capG"                );
			double capH          = reader.unmarshalDouble ("capH"                );
			magCompFn            = MagCompFn.makeLegacyPage (capG, capH);
			magCat               = reader.unmarshalDouble ("magCat"              );
			dataStartTimeDays    = reader.unmarshalDouble ("dataStartTimeDays"   );
			dataEndTimeDays      = reader.unmarshalDouble ("dataEndTimeDays"     );
			numAftershocks       = reader.unmarshalInt    ("numAftershocks"      , 0);

		}
		break;

		case MARSHAL_VER_2: {

			magCompFn            = MagCompFn.unmarshal_poly (reader, "magCompFn" );
			magCat               = reader.unmarshalDouble ("magCat"              );
			dataStartTimeDays    = reader.unmarshalDouble ("dataStartTimeDays"   );
			dataEndTimeDays      = reader.unmarshalDouble ("dataEndTimeDays"     );
			numAftershocks       = reader.unmarshalInt    ("numAftershocks"      , 0);

		}
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
	public RJ_Summary_SequenceSpecific unmarshal (MarshalReader reader, String name) {
		reader.unmarshalMapBegin (name);
		do_umarshal (reader);
		reader.unmarshalMapEnd ();
		return this;
	}

	// Marshal object, polymorphic.

	public static void marshal_poly (MarshalWriter writer, String name, RJ_Summary_SequenceSpecific obj) {

		writer.marshalMapBegin (name);

		if (obj == null) {
			writer.marshalInt (M_TYPE_NAME, MARSHAL_NULL);
		} else {
			writer.marshalInt (M_TYPE_NAME, obj.get_marshal_type());
			obj.do_marshal (writer);
		}

		writer.marshalMapEnd ();

		return;
	}

	// Unmarshal object, polymorphic.

	public static RJ_Summary_SequenceSpecific unmarshal_poly (MarshalReader reader, String name) {
		RJ_Summary_SequenceSpecific result;

		reader.unmarshalMapBegin (name);
	
		// Switch according to type

		int type = reader.unmarshalInt (M_TYPE_NAME);

		switch (type) {

		default:
			throw new MarshalException ("RJ_Summary_SequenceSpecific.unmarshal_poly: Unknown class type code: type = " + type);

		case MARSHAL_NULL:
			result = null;
			break;

		case MARSHAL_RJSEQ:
			result = new RJ_Summary_SequenceSpecific();
			result.do_umarshal (reader);
			break;
		}

		reader.unmarshalMapEnd ();

		return result;
	}
	
}
