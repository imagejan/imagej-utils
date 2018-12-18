/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package de.embl.cba.bdv.utils.labels;

import net.imglib2.converter.Converter;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileARGBType;

import java.util.HashSet;
import java.util.Set;

/**
 * Conversion logic adapted from BigCat Viewer.
 */
public class VolatileLabelsARGBConverter< V extends AbstractVolatileRealType > implements Converter< V, VolatileARGBType >
{

	private long seed = 50;

	@Override
	public void convert( final V input, final VolatileARGBType output )
	{
		if ( input.isValid() )
		{
			final double x = input.getRealDouble();

			output.set( LabelUtils.getColorGlasbey( x, seed ) );
			output.setValid( true );
		}
		else
		{
			output.setValid( false );
		}
	}

	public void incrementRandomColorGeneratorSeed()
	{
		this.seed++;
	}

}