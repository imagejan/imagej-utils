package de.embl.cba.bdv.utils.render;

import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvHandle;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import de.embl.cba.bdv.utils.sources.Metadata;
import de.embl.cba.bdv.utils.sources.Sources;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

public class AccumulateEMAndFMProjectorARGB extends AccumulateProjector< ARGBType, ARGBType >
{
	public static AccumulateProjectorFactory< ARGBType > factory = new AccumulateProjectorFactory< ARGBType >()
	{
		@Override
		public AccumulateEMAndFMProjectorARGB createAccumulateProjector(
				final ArrayList< VolatileProjector > sourceProjectors,
				final ArrayList< Source< ? > > sources,
				final ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
				final RandomAccessibleInterval< ARGBType > targetScreenImage,
				final int numThreads,
				final ExecutorService executorService )
		{
			return new AccumulateEMAndFMProjectorARGB(
					sourceProjectors,
					sources,
					sourceScreenImages,
					targetScreenImage,
					numThreads,
					executorService );
		}
	};
	
	private final ArrayList< Source< ? > > sourceList;

	public AccumulateEMAndFMProjectorARGB(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< Source< ? > > sources,
			final ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
			final RandomAccessibleInterval< ARGBType > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sourceScreenImages, target, numThreads, executorService );
		this.sourceList = sources;
	}

	@Override
	protected void accumulate(
			final Cursor< ? extends ARGBType >[] accesses,
			final ARGBType target )
	{
		int aAvg = 0, rAvg = 0, gAvg = 0, bAvg = 0, numNonZeroAvg = 0;
		int aAccu = 0, rAccu = 0, gAccu = 0, bAccu = 0;

		int sourceIndex = 0;

		final double[] position = new double[ 3 ];
		for ( final Cursor< ? extends ARGBType > access : accesses )
		{
			final int value = access.get().get();
			access.localize( position );
			final int a = ARGBType.alpha( value );
			final int r = ARGBType.red( value );
			final int g = ARGBType.green( value );
			final int b = ARGBType.blue( value );

			if ( a == 0 ) continue; // TODO: think about this!

			Source< ? > source = sourceList.get( sourceIndex++ );
			if ( source instanceof TransformedSource  )
				source = ((TransformedSource)source).getWrappedSource();

			final Metadata metadata = Sources.sourceToMetadata.get( source );

			if ( metadata == null || ! metadata.modality.equals( Metadata.Modality.EM ) )
			{
				aAccu += a;
				rAccu += r;
				gAccu += g;
				bAccu += b;
			}
			else
			{
				aAvg += a;
				rAvg += r;
				gAvg += g;
				bAvg += b;
				numNonZeroAvg++;
			}
		}

		if ( numNonZeroAvg > 0 )
		{
			aAvg /= numNonZeroAvg;
			rAvg /= numNonZeroAvg;
			gAvg /= numNonZeroAvg;
			bAvg /= numNonZeroAvg;
		}

		aAccu += aAvg;
		rAccu += rAvg;
		gAccu += gAvg;
		bAccu += bAvg;

		if ( aAccu > 255 )
			aAccu = 255;
		if ( rAccu > 255 )
			rAccu = 255;
		if ( gAccu > 255 )
			gAccu = 255;
		if ( bAccu > 255 )
			bAccu = 255;

		target.set( ARGBType.rgba( rAccu, gAccu, bAccu, aAccu ) );
	}

}
