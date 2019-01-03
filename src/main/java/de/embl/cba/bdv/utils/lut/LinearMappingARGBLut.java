package de.embl.cba.bdv.utils.lut;

import java.util.Map;

@Deprecated
public class LinearMappingARGBLut implements ARGBLut
{
	Map< Double, Number > map;
	double min, max;

	byte[][] lut;

	public LinearMappingARGBLut( Map< Double, Number > map, double min, double max )
	{
		this.map = map;
		this.min = min;
		this.max = max;

		this.lut = Luts.GRAYSCALE;
	}

	public LinearMappingARGBLut( Map< Double, Number > map, byte[][] lut, double min, double max )
	{
		this.map = map;
		this.min = min;
		this.max = max;
		this.lut = lut;
	}

	@Override
	public int getARGBIndex( double x, double brightness )
	{
		final byte lutIndex = (byte) ( 255.0 * ( map.get( x ).doubleValue() - min ) / ( max - min ) );

		final int argbIndex = Luts.getARGBIndex( lutIndex, lut, brightness );

		return argbIndex;
	}

	public void setLut( byte[][] lut )
	{
		this.lut = lut;
	}

	public void setMap( Map< Double, Number > map )
	{
		this.map = map;
	}

}
